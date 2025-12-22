# Traghetti Ferry MAUI

Applicazione .NET MAUI per visualizzare gli orari dei traghetti Delcomar e Maddalena Lines tra Palau e La Maddalena.

## Caratteristiche

- **Rilevamento GPS automatico**: determina automaticamente se sei a La Maddalena o Palau
- **Selezione manuale posizione**: possibilità di impostare manualmente il porto di partenza
- **Visualizzazione orari**: mostra gli ultimi 3 traghetti passati (sbiaditi) e i prossimi 6 disponibili
- **Selezione data/ora**: visualizza gli orari per qualsiasi momento selezionato
- **Due compagnie**: Delcomar (20 min traversata) e Maddalena Lines (15 min traversata)
- **Design moderno**: interfaccia Material Design con colori distintivi per le compagnie

## Requisiti

- .NET 8.0 SDK
- Visual Studio 2022 17.8+ con workload .NET MAUI
- Per Android: Android SDK 21+
- Per Windows: Windows 10 1809+

## Compilazione

```bash
dotnet restore
dotnet build -f net8.0-android34.0
```

## Esecuzione

### Android
```bash
dotnet build -t:Run -f net8.0-android34.0
```

### Windows
```bash
dotnet build -t:Run -f net8.0-windows10.0.19041.0
```

## Permessi

L'applicazione richiede i permessi di localizzazione per il rilevamento GPS automatico:
- Android: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
- Windows: Location capability

## Architettura

Progetto basato su architettura MVVM con:
- **Models**: Ferry, FerryLocation
- **Services**: FerryService, LocationService
- **ViewModels**: MainViewModel
- **Views**: MainPage

## Librerie Utilizzate

- CommunityToolkit.Maui 7.0.1
- CommunityToolkit.Mvvm 8.2.2
- Microsoft.Maui.Controls 8.0.3
