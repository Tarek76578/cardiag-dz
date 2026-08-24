# CarDiag — UI/UX Vehicle Experience

## Goal
Create a premium automotive diagnostic experience where the vehicle is the center of the product. Selecting a model opens a complete technical vehicle profile rather than a simple model card.

## Information architecture
Home → My Vehicle / Vehicle Catalog → Make → Model → Generation → Engine / Trim → Vehicle Profile → Diagnose

## Home
- Hero vehicle card: image, make, model, year, engine, health status.
- Quick actions: OBD Scanner, Diagnose, VIN, Live Data.
- Recent diagnostic sessions.
- Vehicle catalog entry point.

## Vehicle Profile
Header:
- Vehicle image
- Make + model
- Generation
- Production years
- Favorite / Add to Garage
- Primary action: Diagnose this vehicle

Tabs:
1. Overview
2. Engine
3. Specifications
4. ECU & OBD
5. DTC & Faults
6. Diagnostics

## Overview
Show vehicle identity, body type, production years, market, trim, health state, and key specifications.

## Engine
Show every available engine variant with:
- Engine code
- Fuel type
- Displacement (cc/L)
- Cylinders
- Aspiration / Turbo
- Injection system
- Power (HP/kW)
- Torque (Nm)
- Production years
- Transmission compatibility
- ECU

Selecting an engine opens an engine detail sheet/page with all technical specifications and compatible diagnostics.

## Specifications
Show grouped technical data:
- Dimensions
- Weight
- Wheelbase
- Fuel tank
- Consumption
- CO2
- Tires / wheels
- Brakes
- Transmission
- Performance

## ECU & OBD
Show ECU modules, supported protocols, supported PIDs, VIN availability, and diagnostic capabilities.

## DTC & Faults
For the selected vehicle/engine show compatible diagnostic codes, fault descriptions, symptoms, severity, probable causes, diagnostic tests, repairs and compatible parts.

## UX principles
- Arabic RTL and French LTR are first-class layouts, not literal translations.
- Progressive disclosure: show the most useful information first, technical depth behind clear sections.
- Never use fake/static vehicle data when Supabase data is available.
- Empty/loading/error states must be designed.
- The main CTA always remains easy to reach: Diagnose this vehicle.
- Use consistent automotive status semantics: Healthy, Attention, Critical.
- Images are supplied from vehicle_images and must support graceful placeholders.

## Supabase mapping
vehicle_makes → vehicle_models → vehicle_generations → vehicle_engines → vehicle_trims → vehicle_specifications → vehicle_ecus → vehicle_images → diagnostic_codes → faults → repairs → parts.

## Design direction
Premium automotive dashboard: dark/light capable, strong hierarchy, large vehicle imagery, compact technical data cards, restrained status indicators, rounded surfaces, clear typography, and minimal clutter. The vehicle should feel like the user's digital asset and diagnostic hub.
