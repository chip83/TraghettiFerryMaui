using TraghettiFerryMaui.Models;

namespace TraghettiFerryMaui.Services;

public interface IFerryService
{
    List<Ferry> GetFerrySchedule(FerryLocation location, DateTime selectedDateTime);
}