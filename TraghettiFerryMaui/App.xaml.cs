using Microsoft.Maui;
using Microsoft.Maui.Controls;
using TraghettiFerryMaui.Views;

namespace TraghettiFerryMaui;

public partial class App : Application
{
    public App(MainPage mainPage)
    {
        InitializeComponent();
        MainPage = new NavigationPage(mainPage);
    }
}