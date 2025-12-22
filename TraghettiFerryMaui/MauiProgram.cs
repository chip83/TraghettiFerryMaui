using CommunityToolkit.Maui;
using Microsoft.Extensions.Logging;
using TraghettiFerryMaui.Services;
using TraghettiFerryMaui.ViewModels;
using TraghettiFerryMaui.Views;

namespace TraghettiFerryMaui;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiCommunityToolkit()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
            });

        // Services
        builder.Services.AddSingleton<IFerryService, FerryService>();
        builder.Services.AddSingleton<ILocationService, LocationService>();

        // ViewModels
        builder.Services.AddSingleton<MainViewModel>();

        // Views
        builder.Services.AddSingleton<MainPage>();

#if DEBUG
        builder.Logging.AddDebug();
#endif

        return builder.Build();
    }
}