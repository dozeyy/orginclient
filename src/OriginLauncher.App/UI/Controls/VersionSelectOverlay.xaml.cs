using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using OriginLauncher.App.Core;
using OriginLauncher.App.Core.Versions;

namespace OriginLauncher.App.UI.Controls;

// Full-screen version picker that overlays Home. Two states: a 3x3 grid of
// artwork cards (the landing), and — once a card is picked — a master/detail
// view with the families stacked down the left and the selected family's detail
// (hero image, description, specific-version picker, Launch + Mods) on the right.
// Cards are ListBox items so a click reliably selects; the control owns no
// launch logic beyond raising LaunchRequested.
public partial class VersionSelectOverlay : UserControl
{
    private static readonly Duration Fade = new(TimeSpan.FromSeconds(0.14));

    private bool _syncing;

    public VersionSelectOverlay()
    {
        InitializeComponent();
        GridList.ItemsSource = VersionCatalog.Groups;
        StackList.ItemsSource = VersionCatalog.Groups;
    }

    /// <summary>Raised when the player picks a supported specific version (keeps
    /// Home's current selection in sync as they browse).</summary>
    public event Action<string>? VersionSelected;

    /// <summary>Raised when the player clicks Launch on a supported version.</summary>
    public event Action<string>? LaunchRequested;

    public bool IsOpen => Visibility == Visibility.Visible;

    public void Show(string? currentVersion)
    {
        // Reset to the grid landing each time it opens.
        ToGridState(animate: false);
        _syncing = true;
        GridList.SelectedItem = null;
        StackList.SelectedItem = null;
        _syncing = false;

        Visibility = Visibility.Visible;
        BeginAnimation(OpacityProperty, null);
        Opacity = 0;
        BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, Fade));
        Focus();
    }

    private void CloseOverlay()
    {
        var fade = new DoubleAnimation(1, 0, Fade);
        fade.Completed += (_, _) => Visibility = Visibility.Collapsed;
        BeginAnimation(OpacityProperty, fade);
    }

    // ---- state transitions ----

    private void ToGridState(bool animate)
    {
        DetailState.Visibility = Visibility.Collapsed;
        BackButton.Visibility = Visibility.Collapsed;
        GridState.Visibility = Visibility.Visible;
        if (animate)
        {
            GridState.BeginAnimation(OpacityProperty, null);
            GridState.Opacity = 0;
            GridState.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, Fade));
        }
        else
        {
            GridState.BeginAnimation(OpacityProperty, null);
            GridState.Opacity = 1;
        }
    }

    private void ToDetailState(VersionGroup group)
    {
        GridState.Visibility = Visibility.Collapsed;
        DetailState.Visibility = Visibility.Visible;
        BackButton.Visibility = Visibility.Visible;

        _syncing = true;
        StackList.SelectedItem = group;
        _syncing = false;
        StackList.ScrollIntoView(group);
        ShowGroupDetail(group);

        // Fade + a small slide-in so the switch reads as the cards moving aside.
        DetailState.BeginAnimation(OpacityProperty, null);
        DetailState.Opacity = 0;
        DetailState.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, Fade));
        var slide = new DoubleAnimation(-28, 0, Fade) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
        DetailSlide.BeginAnimation(TranslateTransform.XProperty, slide);
    }

    // Populate the right-hand detail for a family.
    private void ShowGroupDetail(VersionGroup group)
    {
        DetailPanel.DataContext = group;
        VersionDropdown.ItemsSource = group.Versions;
        VersionDropdown.SelectedItem =
            group.Versions.FirstOrDefault(v => v.Id == group.NewestSupported)
            ?? group.Versions.FirstOrDefault();

        var playable = group.Versions.Count(v => v.Supported);
        FactsText.Text = group.AnySupported
            ? $"{group.Versions.Count} versions · {playable} playable now · Fabric + Sodium + Iris shaders"
            : $"{group.Versions.Count} versions · not yet available in Origin";

        ChipsPanel.Visibility = group.AnySupported ? Visibility.Visible : Visibility.Collapsed;
        ComingSoonNote.Visibility = group.AnySupported ? Visibility.Collapsed : Visibility.Visible;
        UpdateActionState();
    }

    private void UpdateActionState()
    {
        var entry = VersionDropdown.SelectedItem as VersionEntry;
        LaunchButton.IsEnabled = entry is { Supported: true };
        ModsButton.IsEnabled = entry != null;
        // Keep Home's selection in step with a supported pick as the user browses.
        if (entry is { Supported: true })
            VersionSelected?.Invoke(entry.Id);
    }

    // ---- events ----

    private void GridList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_syncing || GridList.SelectedItem is not VersionGroup group) return;
        ToDetailState(group);
    }

    private void StackList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_syncing || StackList.SelectedItem is not VersionGroup group) return;
        ShowGroupDetail(group);
    }

    private void VersionDropdown_SelectionChanged(object sender, SelectionChangedEventArgs e) => UpdateActionState();

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        _syncing = true;
        GridList.SelectedItem = null;   // so re-clicking the same card fires again
        _syncing = false;
        ToGridState(animate: true);
    }

    private void Launch_Click(object sender, RoutedEventArgs e)
    {
        if (VersionDropdown.SelectedItem is VersionEntry { Supported: true } entry)
        {
            LaunchRequested?.Invoke(entry.Id);
            CloseOverlay();
        }
    }

    private void Mods_Click(object sender, RoutedEventArgs e)
    {
        var version = (VersionDropdown.SelectedItem as VersionEntry)?.Id
                      ?? (DetailPanel.DataContext as VersionGroup)?.NewestSupported;
        if (version == null) return;
        try
        {
            var mods = Path.Combine(OriginPaths.Instances, version, "mods");
            Directory.CreateDirectory(mods);
            Process.Start(new ProcessStartInfo { FileName = mods, UseShellExecute = true });
        }
        catch
        {
            // Opening a folder is best-effort — never crash the picker over it.
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e) => CloseOverlay();

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (e.Key != Key.Escape) return;
        if (DetailState.Visibility == Visibility.Visible) Back_Click(this, new RoutedEventArgs());
        else CloseOverlay();
        e.Handled = true;
    }
}
