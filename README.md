# TER — Expérimentation des limites des tokens JWT en contexte d'accès médical

Ce projet Spring Boot démontre la différence entre :

- **Validité technique** d'un JWT (signature + expiry vérifiés par Spring Security)
- **Légitimité contextuelle** (contrainte horaire métier appliquée côté serveur)

---

## Architecture

```
com.example.resourceserver
├── ResourceServerDemoApplication.java      — point d'entrée Spring Boot
├── config/
│   ├── KeycloakJwtAuthConverter.java       — mapping rôle Keycloak → authority Spring
│   └── SecurityConfig.java                 — protection des endpoints + validation JWT
├── policy/
│   ├── AccessWindowProperties.java         — propriétés plage horaire (application.yml)
│   ├── PolicyConfig.java                   — activation des @ConfigurationProperties
│   └── WorkingHoursService.java            — vérification de la plage horaire
└── api/
    ├── MedicalController.java              — les 4 endpoints REST
    └── ApiExceptionHandler.java            — gestion des 403 Forbidden
```

### Endpoints

| Endpoint | Auth | Contrainte |
|---|---|---|
| `GET /api/public/ping` | aucune | — |
| `GET /api/medical/standard` | JWT valide + rôle `NURSE` | aucune |
| `GET /api/medical/contextual` | JWT valide + rôle `NURSE` | plage horaire métier |
| `GET /api/medical/token-info` | JWT valide + rôle `NURSE` | — |

---

## Configuration Keycloak

### Prérequis

- Keycloak démarré sur `http://localhost:8080`

### 1. Créer le Realm

1. Connecte-toi à la console admin : `http://localhost:8080/admin`
2. Crée un realm **`ter-realm`**

### 2. Créer le Client

Dans `ter-realm` → **Clients** → **Create client** :

| Champ | Valeur |
|---|---|
| Client ID | `ter-client` |
| Client Protocol | `openid-connect` |
| Access Type | `public` (ou `confidential` pour le secret) |
| Direct Access Grants | activé (pour les tests via `password` grant) |
| Valid Redirect URIs | `http://localhost:8081/*` |

### 3. Créer le Rôle Realm

Dans `ter-realm` → **Realm Roles** → **Create role** :

| Champ | Valeur |
|---|---|
| Role Name | `NURSE` |

### 4. Créer l'Utilisateur Alice

Dans `ter-realm` → **Users** → **Add user** :

| Champ | Valeur |
|---|---|
| Username | `alice` |
| Email | `alice@example.com` |
| Email Verified | ✅ |

Onglet **Credentials** → **Set Password** (ex. `alice123`, non temporaire).

Onglet **Role Mappings** → **Realm Roles** → assigner **`NURSE`** à Alice.

---

## Lancement du serveur

```powershell
# Windows — PowerShell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
./mvnw spring-boot:run
```

```bash
# Linux / macOS
./mvnw spring-boot:run
```

Le serveur démarre sur `http://localhost:8081`.

---

## Protocole de validation

### Étape 1 — Obtenir un token JWT

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/ter-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ter-client" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" \
  | jq -r '.access_token')

echo "Token: $TOKEN"
```

> **PowerShell (Windows) :**
> ```powershell
> $response = Invoke-RestMethod -Uri "http://localhost:8080/realms/ter-realm/protocol/openid-connect/token" `
>   -Method Post `
>   -ContentType "application/x-www-form-urlencoded" `
>   -Body "client_id=ter-client&username=alice&password=alice123&grant_type=password"
> $TOKEN = $response.access_token
> ```

---

### Étape 2 — Appeler les endpoints

#### Endpoint public (sans token)

```bash
curl http://localhost:8081/api/public/ping
```

Réponse attendue : `200 OK`

```json
{
  "endpoint": "/api/public/ping",
  "status": "UP",
  "message": "Service is running — no token required"
}
```

---

#### Endpoint standard (JWT + rôle NURSE)

```bash
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8081/api/medical/standard
```

Réponse attendue : `200 OK` tant que le JWT n'est pas expiré.

```json
{
  "user": "alice",
  "issued_at": "...",
  "expires_at": "...",
  "age_seconds": 42,
  "remaining_seconds": 258,
  "token_valid": true,
  "endpoint": "/api/medical/standard",
  "access": "GRANTED — JWT valid, role NURSE confirmed",
  "contextual_check": false
}
```

---

#### Endpoint contextuel (JWT + rôle NURSE + plage horaire)

```bash
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8081/api/medical/contextual
```

**Dans la plage horaire** (`08:00–18:00`) → `200 OK`

```json
{
  "endpoint": "/api/medical/contextual",
  "access": "GRANTED — JWT valid + within working hours",
  "contextual_check": true
}
```

**Hors plage horaire** → `403 Forbidden`

```json
{
  "endpoint": "/api/medical/contextual",
  "access": "DENIED — outside working hours",
  "jwt_valid": true,
  "user": "alice"
}
```

---

#### Informations du token

```bash
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8081/api/medical/token-info
```

Retourne le détail complet des claims du JWT décodé.

---

### Étape 3 — Observer la différence de comportement

| Situation | `/api/medical/standard` | `/api/medical/contextual` |
|---|---|---|
| Dans la plage horaire, JWT valide | ✅ `200 OK` | ✅ `200 OK` |
| Hors plage horaire, JWT valide | ✅ `200 OK` | ❌ `403 Forbidden` |
| JWT expiré | ❌ `401 Unauthorized` | ❌ `401 Unauthorized` |
| Pas de JWT | ❌ `401 Unauthorized` | ❌ `401 Unauthorized` |
| JWT valide mais rôle NURSE absent | ❌ `403 Forbidden` | ❌ `403 Forbidden` |

---

## Configuration de la plage horaire

Dans `src/main/resources/application.yml` :

```yaml
access:
  window:
    start: "08:00"   # début de la plage autorisée (format HH:mm, 24h)
    end:   "18:00"   # fin de la plage autorisée
```

Modifie ces valeurs pour tester le comportement à différentes heures.

---

## Postman

Importe la collection suivante ou crée les requêtes manuellement :

1. **GET** `http://localhost:8081/api/public/ping` — aucun header
2. **GET** `http://localhost:8081/api/medical/standard`
   - Header: `Authorization: Bearer <votre_token>`
3. **GET** `http://localhost:8081/api/medical/contextual`
   - Header: `Authorization: Bearer <votre_token>`
4. **GET** `http://localhost:8081/api/medical/token-info`
   - Header: `Authorization: Bearer <votre_token>`

Pour obtenir le token dans Postman :
- **POST** `http://localhost:8080/realms/ter-realm/protocol/openid-connect/token`
- Body (form-urlencoded) : `client_id=ter-client`, `username=alice`, `password=alice123`, `grant_type=password`

---

## Résumé de l'expérimentation

> **Un JWT techniquement valide (signature correcte, non expiré) n'est pas forcément
> légitime dans un contexte métier.**
>
> `/api/medical/standard` illustre la validation purement technique Spring Security.
> `/api/medical/contextual` ajoute la contrainte contextuelle (horaire).
> Le même token peut donc obtenir `200` sur l'un et `403` sur l'autre.
