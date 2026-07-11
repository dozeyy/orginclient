using System.Windows;
using System.Windows.Threading;
using OriginLauncher.App.Core;
using OriginLauncher.App.UI.Windows;

namespace OriginLauncher.App;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        OriginPaths.EnsureScaffold();

        // A single stray exception used to take the whole launcher down with
        // nothing to show for it. Route every unhandled failure through the
        // crash reporter (saved log + copy-paste dialog) instead.
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnDomainUnhandledException;
        System.Threading.Tasks.TaskScheduler.UnobservedTaskException += (_, args) => args.SetObserved();
    }

    // UI-thread exceptions are usually survivable (a bad event handler, a render
    // hiccup) — show the report, then keep the launcher alive.
    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        CrashReporter.Report(e.Exception, fatal: false);
        e.Handled = true;
    }

    // Background-thread exceptions can't be swallowed — the runtime is tearing
    // the process down. Show the report before it goes.
    private void OnDomainUnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex)
            CrashReporter.Report(ex, fatal: true);
    }
}
