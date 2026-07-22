using System.Windows;
using System.Windows.Controls;
using Microsoft.Web.WebView2.Core;
using OriginLauncher.App.Core.Auth;

namespace OriginLauncher.App.UI.Controls;

// Hosts the Microsoft OAuth login page inside the launcher via WebView2,
// instead of the old approach of shelling out to the system's default
// browser. MicrosoftAuthenticator still owns the whole MSA -> Xbox Live ->
// XSTS -> Minecraft chain and the loopback redirect listener; this control
// only supplies "how to show the user the authorization URL".
public partial class MicrosoftSignInPanel : UserControl, IDisposable
{
    public event EventHandler<AuthResult>? SignInSucceeded;
    public event EventHandler<string>? SignInFailed;
    public event EventHandler? Cancelled;

    private readonly CancellationTokenSource _cts = new();
    private bool _disposed;

    public MicrosoftSignInPanel()
    {
        InitializeComponent();
        Loaded += MicrosoftSignInPanel_Loaded;
    }

    private async void MicrosoftSignInPanel_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await WebView.EnsureCoreWebView2Async();
            WebView.CoreWebView2.NavigationCompleted += (_, _) => LoadingOverlay.Visibility = Visibility.Collapsed;

            var result = MicrosoftAuthenticator.UseMinecraftClientId
                ? await SignInViaWebViewAsync(_cts.Token)
                : await new MicrosoftAuthenticator().SignInAsync(url => WebView.Source = new Uri(url), _cts.Token);

            SignInSucceeded?.Invoke(this, result);
        }
        catch (OperationCanceledException)
        {
            // User closed the panel — not a failure, nothing to show.
        }
        catch (TimeoutException)
        {
            SignInFailed?.Invoke(this, "Sign-in timed out — no response after 5 minutes.");
        }
        catch (MicrosoftAuthException ex)
        {
            SignInFailed?.Invoke(this, ex.Message);
        }
        catch (Exception ex)
        {
            SignInFailed?.Invoke(this, $"Sign-in failed: {ex.Message}");
        }
    }

    // The shipping sign-in flow (grandfathered Minecraft client ID). Its
    // registered redirect is Live Connect's blank oauth20_desktop.srf landing
    // page rather than a localhost loopback. Since sign-in happens inside our
    // own WebView2, we don't need to actually load that page: WebView2 fires
    // NavigationStarting for the redirect URI first, so we intercept it there,
    // pull the authorization code out of the query, and cancel before it ever
    // navigates anywhere.
    private async Task<AuthResult> SignInViaWebViewAsync(CancellationToken ct)
    {
        var authenticator = new MicrosoftAuthenticator();
        var (authUrl, codeVerifier) = authenticator.BuildAuthorizationRequest();

        var codeSource = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);

        void OnNavigationStarting(object? s, CoreWebView2NavigationStartingEventArgs args)
        {
            if (!args.Uri.StartsWith(MicrosoftAuthenticator.LiveDesktopRedirectUri, StringComparison.Ordinal)) return;
            args.Cancel = true;

            var query = ParseQuery(new Uri(args.Uri).Query);
            if (query.TryGetValue("code", out var code))
                codeSource.TrySetResult(code);
            else
                codeSource.TrySetException(new MicrosoftAuthException("login",
                    $"Microsoft sign-in did not return an authorization code ({(query.TryGetValue("error", out var err) ? err : "unknown error")})."));
        }

        WebView.CoreWebView2.NavigationStarting += OnNavigationStarting;
        string authCode;
        try
        {
            WebView.Source = new Uri(authUrl);
            authCode = await codeSource.Task.WaitAsync(TimeSpan.FromMinutes(5), ct);
        }
        finally
        {
            WebView.CoreWebView2.NavigationStarting -= OnNavigationStarting;
        }

        return await authenticator.CompleteSignInAsync(authCode, codeVerifier, ct);
    }

    private static Dictionary<string, string> ParseQuery(string query)
    {
        var result = new Dictionary<string, string>();
        foreach (var pair in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var idx = pair.IndexOf('=');
            if (idx < 0) continue;
            result[Uri.UnescapeDataString(pair[..idx])] = Uri.UnescapeDataString(pair[(idx + 1)..]);
        }
        return result;
    }

    private void CancelButton_Click(object sender, RoutedEventArgs e)
    {
        _cts.Cancel();
        Cancelled?.Invoke(this, EventArgs.Empty);
    }

    // MainWindow creates a brand-new MicrosoftSignInPanel (and its embedded
    // WebView2) every time the user clicks "Add Microsoft Account". WebView2
    // does not reliably release its browser process / user data folder lock
    // just because it's removed from the visual tree (Content = null) — the
    // WPF control's cleanup on Unload is not synchronous, so a second panel
    // created before the first one is GC'd can find the environment still
    // held and fail to load. Disposing explicitly the moment the panel closes
    // is what Microsoft's own WebView2 WPF samples do for exactly this
    // reason: it's what makes repeated sign-in attempts (a 2nd, 3rd account)
    // reliable instead of intermittently silently failing.
    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;

        _cts.Cancel();
        _cts.Dispose();
        WebView.Dispose();
    }
}
