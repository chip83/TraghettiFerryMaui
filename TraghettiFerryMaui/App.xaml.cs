using TraghettiFerryMaui.Views;

namespace TraghettiFerryMaui;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        MainPage = new NavigationPage(new MainPage());
    }
}