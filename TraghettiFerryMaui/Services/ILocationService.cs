using TraghettiFerryMaui.Models;

namespace TraghettiFerryMaui.Services;

public interface ILocationService
{
    Task<FerryLocation> GetCurrentLocationAsync();
}