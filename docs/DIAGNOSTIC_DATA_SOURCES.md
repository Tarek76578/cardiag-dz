# CarDiag Diagnostic Data Sources

## Policy

CarDiag must not infer or fabricate vehicle-specific ECU, engine, DTC, symptom, test, or repair relationships. Vehicle-specific facts require a traceable source and review status.

## Market-priority sources

- Focus2move — Algeria 2025 vehicle sales report: https://www.focus2move.com/algerian-vehicles-sales-3/
- Autobip — popular used vehicles in Algeria: https://www.autobip.com/fr/voitures-populaires-occasion-algerie
- OpenSooq Algeria — top sold vehicles: https://dz.opensooq.com/en/top-sold-cars

These sources are used only to prioritize coverage work. They are not treated as technical diagnostic authority.

## Technical diagnostic sources

- ISO 15031-6:2015 — standardized DTC definitions: https://www.iso.org/standard/66369.html
- SAE J2012 — standardized DTC definitions: https://saemobilus.sae.org/standards/j2012_201612-diagnostic-trouble-code-definitions
- OBDb / OBDb Community — community OBD parameters and DTC documentation: https://obdb.community/

## Coverage workflow

1. Select a model-year from `diagnostic_coverage_queue`.
2. Establish engine and ECU applicability from a traceable technical source.
3. Add standardized DTC definitions separately from manufacturer-specific applicability.
4. Add symptoms, diagnostic tests, and repairs only when supported by a technical source.
5. Record `source_id`, confidence, and review status.
6. Do not mark a record `verified` without review.

## Algeria-first priority

Current market evidence supports prioritizing Fiat, Renault, Peugeot, and Dacia, followed by Volkswagen, Hyundai, Kia, Toyota, Chery, Geely, Opel, Nissan, Citroën, SEAT, Škoda, Ford, Suzuki, Mercedes-Benz, Audi, and BMW. This is a prioritization rule, not a claim that these brands cover the entire Algerian fleet.
