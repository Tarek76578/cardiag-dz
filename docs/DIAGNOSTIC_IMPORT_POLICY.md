# CarDiag diagnostic import policy

## Purpose
Import verified vehicle/engine/ECU/DTC relationships without inventing compatibility data.

## Evidence levels
- `5`: OEM documentation, manufacturer service information, or authoritative standard.
- `4`: reputable technical manual/bulletin or verified database with traceable provenance.
- `3`: established community database; requires review before production use.
- `1-2`: unverified/community-only claim; never auto-promote to production.

## Rules
1. A vehicle-year/engine/ECU relationship must have a source and confidence before it is marked `verified`.
2. A DTC definition may use the standardized DTC reference, but vehicle-specific applicability requires separate evidence.
3. Never infer an ECU from make/model/year alone.
4. Never infer a repair procedure from a generic DTC description alone.
5. `ai_generated` knowledge is draft-only and cannot be promoted automatically.
6. Every imported relationship must retain provenance and review status.

## Initial Algeria priority
Prioritize brands/models supported by current Algeria market evidence. Current external market evidence identifies Fiat as a leading 2025 brand and documents local production of Fiat 500 Hybrid and Doblo variants. This establishes prioritization, not ECU compatibility.

## Current authoritative DTC basis
Use ISO 15031-6:2015 / SAE J2012-derived definitions as the standardized DTC reference. ISO 15031-6:2015 describes standardized DTC format and references SAE J2012-DA for standardized codes and failure types.

## No bulk guessing
The 10k+ coverage queue is a work queue, not permission to manufacture relationships. Records remain `missing`/`review` until evidence is attached.
