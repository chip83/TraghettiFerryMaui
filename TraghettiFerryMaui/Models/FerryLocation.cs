namespace TraghettiFerryMaui.Models;

public enum FerryLocation
{
    LaMaddalena,
    Palau
}

public static class FerryLocationExtensions
{
    public static string ToDisplayString(this FerryLocation location)
    {
        return location switch
        {
            FerryLocation.LaMaddalena => "LA MADDALENA",
            FerryLocation.Palau => "PALAU",
            _ => "LA MADDALENA"
        };
    }
}