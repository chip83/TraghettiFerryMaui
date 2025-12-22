using TraghettiFerryMaui.ViewModels;

namespace TraghettiFerryMaui.Views;

public partial class MainPage : ContentPage
{
    private readonly MainViewModel _viewModel;

    public MainPage(MainViewModel viewModel)
    {
        InitializeComponent();
        _viewModel = viewModel;
        BindingContext = _viewModel;
    }

    private void OnManualLocationToggled(object sender, ToggledEventArgs e)
    {
        _viewModel.ToggleManualLocationCommand.Execute(null);
    }

    private async void OnSelectDateTimeClicked(object sender, EventArgs e)
    {
        // Date Picker
        var date = await DisplayPromptAsync("Seleziona Data",
            "Inserisci la data (gg/mm/aaaa):",
            initialValue: _viewModel.SelectedDate.ToString("dd/MM/yyyy"),
            keyboard: Keyboard.Numeric);

        if (!string.IsNullOrEmpty(date) && DateTime.TryParseExact(date, "dd/MM/yyyy",
            System.Globalization.CultureInfo.InvariantCulture,
            System.Globalization.DateTimeStyles.None,
            out var selectedDate))
        {
            _viewModel.SelectedDate = selectedDate;

            // Time Picker
            var time = await DisplayPromptAsync("Seleziona Orario",
                "Inserisci l'orario (hh:mm):",
                initialValue: _viewModel.SelectedTime.ToString(@"hh\:mm"),
                keyboard: Keyboard.Numeric);

            if (!string.IsNullOrEmpty(time) && TimeSpan.TryParseExact(time, @"hh\:mm",
                System.Globalization.CultureInfo.InvariantCulture,
                out var selectedTime))
            {
                _viewModel.SelectedTime = selectedTime;
            }
        }
    }
}