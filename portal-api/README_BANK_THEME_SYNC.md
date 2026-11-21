# Bank Master / Bank Theme synchronization

Changes in this package:
- Creating a bank through `/banks` also creates/updates a row in `public.portal_bank_theme`.
- Editing a bank synchronizes the theme `name` and bank linkage while retaining any existing colour palette and other theme customizations.
- `portal_bank_theme.bank_id` stores the bank master identifier (`MB002001.bank_code`, the master key in this schema).
- Existing theme records are linked on startup where their theme `code` matches a bank code.
- Theme rows use bank code as their `code` key, so login/theme lookup remains consistent.

The backend runs the schema migration automatically (`bank_id` column). Maven compilation could not be verified in this environment because Maven is not installed.
