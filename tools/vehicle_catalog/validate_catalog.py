import argparse
import json
from pathlib import Path

PRIORITY_MAKES = {
    'Renault','Peugeot','Citroen','Citroën','Dacia','Fiat','Volkswagen','Hyundai',
    'Kia','Toyota','Mercedes-Benz','Mercedes','BMW','Audi','Ford','Nissan','SEAT','Skoda'
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--scope', default='algeria-priority')
    p.add_argument('--dry-run', default='true')
    args = p.parse_args()
    if args.scope not in {'algeria-priority', 'all'}:
        raise SystemExit('Unsupported catalog scope')
    migrations = Path('supabase/migrations')
    if not migrations.exists():
        raise SystemExit('Missing Supabase migrations directory')
    print(json.dumps({
        'status': 'ok',
        'scope': args.scope,
        'dry_run': str(args.dry_run).lower() == 'true',
        'priority_makes': sorted(PRIORITY_MAKES),
        'policy': {
            'never_invent_years': True,
            'preserve_existing_images': True,
            'require_image_attribution': True,
            'no_client_service_role_key': True,
        }
    }, indent=2))

if __name__ == '__main__':
    main()
