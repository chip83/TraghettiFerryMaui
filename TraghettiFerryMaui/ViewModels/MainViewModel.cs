using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Collections.ObjectModel;
using TraghettiFerryMaui.Models;
using TraghettiFerryMaui.Services;

namespace TraghettiFerryMaui.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly IFerryService _ferryService;
    private readonly ILocationService _locationService;

    [ObservableProperty]
    private ObservableCollection<Ferry> ferries = new();

    [ObservableProperty]
    private FerryLocation currentLocation = FerryLocation.LaMaddalena;

    [ObservableProperty]
    private DateTime selectedDate = DateTime.Now;

    [ObservableProperty]
    private TimeSpan selectedTime = DateTime.Now.TimeOfDay;

    [ObservableProperty]
    private bool isManualLocation = false;

    [ObservableProperty]
    private string locationText = "Partenza da: LA MADDALENA";

    [ObservableProperty]
    private string dateTimeText = string.Empty;

    public MainViewModel(IFerryService ferryService, ILocationService locationService)
    {
        _ferryService = ferryService;
        _locationService = locationService;
        UpdateDateTimeText();
        _ = InitializeAsync();
    }

    private async Task InitializeAsync()
    {
        await GetLocationAsync();
    }

    [RelayCommand]
    private async Task GetLocationAsync()
    {
        if (!IsManualLocation)
        {
            CurrentLocation = await _locationService.GetCurrentLocationAsync();
            UpdateLocationText();
            LoadFerries();
        }
    }

    [RelayCommand]
    private void ToggleManualLocation()
    {
        if (!IsManualLocation)
        {
            _ = GetLocationAsync();
        }
        else
        {
            UpdateLocationText();
            LoadFerries();
        }
    }

    [RelayCommand]
    private void ChangeLocation(string location)
    {
        CurrentLocation = location == "PALAU" ? FerryLocation.Palau : FerryLocation.LaMaddalena;
        UpdateLocationText();
        LoadFerries();
    }

    [RelayCommand]
    private void SelectDateTime()
    {
        // Trigger will be handled by view
    }

    [RelayCommand]
    private void ResetDateTime()
    {
        SelectedDate = DateTime.Now;
        SelectedTime = DateTime.Now.TimeOfDay;
        UpdateDateTimeText();
        LoadFerries();
    }

    partial void OnSelectedDateChanged(DateTime value)
    {
        UpdateDateTimeText();
        LoadFerries();
    }

    partial void OnSelectedTimeChanged(TimeSpan value)
    {
        UpdateDateTimeText();
        LoadFerries();
    }

    partial void OnCurrentLocationChanged(FerryLocation value)
    {
        UpdateLocationText();
    }

    private void UpdateLocationText()
    {
        LocationText = $"Partenza da: {CurrentLocation.ToDisplayString()}";
    }

    private void UpdateDateTimeText()
    {
        var dateTime = SelectedDate.Date + SelectedTime;
        DateTimeText = $"Orario: {dateTime:dd/MM/yyyy HH:mm}";
    }

    private void LoadFerries()
    {
        var dateTime = SelectedDate.Date + SelectedTime;
        var schedule = _ferryService.GetFerrySchedule(CurrentLocation, dateTime);
        Ferries = new ObservableCollection<Ferry>(schedule);
    }
}