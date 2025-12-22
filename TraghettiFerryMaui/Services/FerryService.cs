using TraghettiFerryMaui.Models;

namespace TraghettiFerryMaui.Services;

public class FerryService : IFerryService
{
    private readonly Dictionary<FerryLocation, List<string>> _delcomarSchedules = new()
    {
        {
            FerryLocation.LaMaddalena,
            new List<string>
            {
                "06:00", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00",
                "10:30", "11:00", "11:30", "12:00", "12:30", "13:00", "13:30", "14:15",
                "15:15", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00",
                "19:30", "20:30", "21:30", "22:30", "23:30", "00:30", "01:30", "03:00",
                "04:00", "05:00"
            }
        },
        {
            FerryLocation.Palau,
            new List<string>
            {
                "06:30", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00", "10:30",
                "11:00", "11:30", "12:00", "12:30", "13:00", "13:30", "14:00", "14:45",
                "15:45", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00", "19:30",
                "20:00", "21:00", "22:00", "23:00", "00:00", "01:00", "02:00", "03:30",
                "04:30", "05:30"
            }
        }
    };

    private readonly Dictionary<FerryLocation, List<string>> _maddalenaLinesSchedules = new()
    {
        {
            FerryLocation.LaMaddalena,
            new List<string>
            {
                "07:15", "07:45", "08:15", "08:45", "09:15", "09:45", "10:15", "10:45",
                "11:15", "11:45", "12:15", "12:45", "13:45", "14:45", "15:45", "16:15",
                "16:45", "17:15", "17:45", "18:15", "18:45", "19:15"
            }
        },
        {
            FerryLocation.Palau,
            new List<string>
            {
                "06:45", "07:15", "07:45", "08:15", "08:45", "09:15", "09:45", "10:15",
                "10:45", "11:15", "11:45", "12:15", "13:15", "14:15", "15:15", "15:45",
                "16:15", "16:45", "17:15", "17:45", "18:15", "18:45"
            }
        }
    };

    public List<Ferry> GetFerrySchedule(FerryLocation location, DateTime selectedDateTime)
    {
        var currentTimeStr = selectedDateTime.ToString("HH:mm");
        var allFerries = new List<Ferry>();

        // Delcomar ferries (20 minutes duration)
        foreach (var time in _delcomarSchedules[location])
        {
            allFerries.Add(new Ferry
            {
                DepartureTime = time,
                ArrivalTime = CalculateArrivalTime(time, 20),
                Company = "Delcomar",
                IsPast = string.Compare(time, currentTimeStr) < 0
            });
        }

        // Maddalena Lines ferries (15 minutes duration)
        foreach (var time in _maddalenaLinesSchedules[location])
        {
            allFerries.Add(new Ferry
            {
                DepartureTime = time,
                ArrivalTime = CalculateArrivalTime(time, 15),
                Company = "Maddalena Lines",
                IsPast = string.Compare(time, currentTimeStr) < 0
            });
        }

        allFerries = allFerries.OrderBy(f => f.DepartureTime).ToList();

        var pastFerries = allFerries.Where(f => f.IsPast).TakeLast(3).ToList();
        var futureFerries = allFerries.Where(f => !f.IsPast).Take(6).ToList();

        return pastFerries.Concat(futureFerries).ToList();
    }

    private string CalculateArrivalTime(string departureTime, int durationMinutes)
    {
        var parts = departureTime.Split(':');
        var hour = int.Parse(parts[0]);
        var minute = int.Parse(parts[1]);

        var totalMinutes = hour * 60 + minute + durationMinutes;
        var arrivalHour = (totalMinutes / 60) % 24;
        var arrivalMinute = totalMinutes % 60;

        return $"{arrivalHour:D2}:{arrivalMinute:D2}";
    }
}