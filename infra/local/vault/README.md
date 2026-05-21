# Local Vault Notes

This directory is reserved for local-only Vault bootstrap material.

The current `docker-compose` setup runs Vault in `dev` mode with:

- HTTP address: `http://localhost:8200`
- root token: `dev-root-token`

This is intentionally insecure and must not be reused outside local development.
