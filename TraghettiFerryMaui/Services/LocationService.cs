using TraghettiFerryMaui.Models;

namespace TraghettiFerryMaui.Services;

public class LocationService : ILocationService
{
    private const double LaMaddalenaLat = 41.2137;
    private const double LaMaddalenaLon = 9.4046;
    private const double PalauLat = 41.1787;
    private const double PalauLon = 9.3645;

    public async Task<FerryLocation> GetCurrentLocationAsync()
    {
        try
        {
            var request = new GeolocationRequest(GeolocationAccuracy.Medium, TimeSpan.FromSeconds(10));
            var location = await Geolocation.Default.GetLocationAsync(request);

            if (location != null)
            {
                var distanceToMaddalena = Location.CalculateDistance(
                    location.Latitude, location.Longitude,
                    LaMaddalenaLat, LaMaddalenaLon,
                    DistanceUnits.Kilometers);

                var distanceToPalau = Location.CalculateDistance(
                    location.Latitude, location.Longitude,
                    PalauLat, PalauLon,
                    DistanceUnits.Kilometers);

                return distanceToMaddalena < distanceToPalau
                    ? FerryLocation.LaMaddalena
                    : FerryLocation.Palau;
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Unable to get location: {ex.Message}");
        }

        return FerryLocation.LaMaddalena;
    }
}