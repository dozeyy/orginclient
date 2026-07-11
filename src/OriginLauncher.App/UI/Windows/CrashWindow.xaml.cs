using System.Windows;

namespace OriginLauncher.App.UI.Windows;

// A dead-simple, self-contained error dialog. Shows a human-readable summary
// plus a copy-paste-ready technical report (exception type, message, stack,
// environment) so the user can search it or hand it off. Deliberately depends
// on nothing from the app's theme resources — if a resource is what broke, this
// window must still render.
public partial class CrashWindow : Window
{
    public CrashWindow(string report, string? savedPath, bool fatal)
    {
        InitializeComponent();
        DetailsBox.Text = report;

        Heading.Text = fatal
            ? "Origin Launcher has to close"
            : "Origin Launcher ran into a problem";
        Summary.Text = fatal
            ? "An unexpected error means the launcher can't keep running. The details below explain exactly what happened — click Copy details, then paste them into a search or send them along so it can be fixed."
            : "Something went wrong, but the launcher recovered and is still open. The details below explain exactly what happened — click Copy details, then paste them into a search or send them along so it can be fixed.";
        CloseButton.Content = fatal ? "Close" : "Continue";

        if (!string.IsNullOrEmpty(savedPath))
            SavedTo.Text = $"A copy of this report was saved to:\n{savedPath}";
        else
            SavedTo.Visibility = Visibility.Collapsed;
    }

    private void Copy_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            Clipboard.SetText(DetailsBox.Text);
            CopyButton.Content = "Copied!";
        }
        catch
        {
            // Clipboard can transiently fail (another app holding it) — leave the
            // text on screen so the user can still select and copy manually.
            CopyButton.Content = "Press Ctrl+C";
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();
}
