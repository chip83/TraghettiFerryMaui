namespace TraghettiFerryMaui.Models;

public class Ferry
{
    public string DepartureTime { get; set; } = string.Empty;
    public string ArrivalTime { get; set; } = string.Empty;
    public string Company { get; set; } = string.Empty;
    public bool IsPast { get; set; }
    public string DisplayTime => $"{DepartureTime} → {ArrivalTime}";
    public Color BackgroundColor => IsPast ? Color.FromArgb("#E0E0E0") : Colors.White;
    public double Opacity => IsPast ? 0.6 : 1.0;
    public Color CompanyColor => Company == "Delcomar" ? Color.FromArgb("#FF6F00") : Color.FromArgb("#1976D2");
}