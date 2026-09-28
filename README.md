# batch-report-app

Deuxième application "cobaye" pour Sentrix Agent, complémentaire à
`loadtest-app`. Là où `loadtest-app` teste CPU + mémoire + logs texte,
`batch-report-app` teste des choses différentes :

- **I/O disque réel** (écriture, lecture, suppression de fichiers) — pour
  voir bouger les métriques `system.disk.io.*` de l'agent.
- **Logs au format JSON** — pour tester le `JsonLogSourceParser` de l'agent,
  jamais exercé par `loadtest-app` (qui n'écrit que du texte/Logback).
- **Une vraie politique de rétention** de fichiers (contrairement au buffer
  H2 de l'agent, qui lui ne purge rien — voir discussion précédente).

## Ce qu'elle fait, toutes les 2 minutes

1. **Écrit un rapport CSV** de ~2000 lignes de fausses transactions dans
   `/app/reports/report-<cycle>.csv` (I/O disque en écriture réelle).
2. **Relit le rapport du cycle précédent** et calcule un total (I/O disque
   en lecture réelle).
3. **Supprime les rapports excédentaires** au-delà de `retention-count`
   (I/O disque en suppression).
4. **Vérifie l'espace disque réel** disponible sur le point de montage des
   rapports ; si le libre passe sous le seuil configuré, émet un vrai `WARN`
   (pas simulé).
5. **Corrompt volontairement 1 rapport sur N** (`corrupt-every-cycles`,
   défaut 10) en y ajoutant une ligne invalide. Au cycle suivant, la lecture
   de ce rapport échoue avec une vraie `NumberFormatException`, loguée en
   `ERROR` — ceci est clairement documenté comme un test, pas un vrai bug.

## Deux canaux de logs distincts

- **Console (texte)** : tous les niveaux (INFO inclus), via SLF4J classique
  — visible avec `docker compose logs -f`, pour ton confort en local.
- **Fichier JSON** (`/var/log/batch-report-app/app.json`) : **tous les
  niveaux aussi, INFO inclus**, via `JsonLogWriter`. C'est ce fichier que
  Sentrix Agent doit lire.

**À savoir** : le `JsonLogSourceParser` de l'agent filtre volontairement
les niveaux INFO/DEBUG/TRACE **de son côté** — donc même si `app.json`
contient des lignes INFO (ce qui permet de vérifier facilement que le
pipeline écriture → volume → lecture fonctionne, sans attendre une vraie
erreur), tu ne verras que les WARN/ERROR/FATAL remonter dans le dashboard.
C'est le comportement normal de l'agent, pas un défaut de l'appli.

## Configuration (`application.yml`)

```yaml
batch:
  reports-dir: /app/reports              # dossier des rapports (interne au conteneur)
  records-per-batch: 2000                # lignes par rapport
  retention-count: 5                     # rapports conservés avant purge
  corrupt-every-cycles: 10               # fréquence de l'erreur simulée
  low-space-warn-threshold-percent: 20.0 # seuil du WARN espace disque
  json-log-file: /var/log/batch-report-app/app.json
```

## Lancer avec Docker

Sous `/home/debian`, à côté de `loadtest-app` :

```bash
mkdir -p /home/debian/logs/batch-report-app
cd /home/debian/batch-report-app
docker compose up -d --build
docker compose logs -f batch-report-app
```

Le dossier `/home/debian/logs/batch-report-app` est déjà couvert par le
volume "parapluie" `/home/debian/logs:/var/log/apps:ro` monté une fois pour
toutes côté agent (voir README de `loadtest-app`) — **aucune modification
du `docker-compose.yml` de l'agent n'est nécessaire**.

## Déclarer la source dans `sentrix-sources.yml`

```yaml
sentrix:
  agent:
    log-sources:
      - name: batch-report-app
        format: json
        paths:
          - /var/log/apps/batch-report-app/app.json
```

Pas de `technology` ni de `logger-pattern` nécessaires ici : contrairement
au format texte, le parser JSON lit directement les champs `level`,
`message`, `timestamp` (et tout champ supplémentaire) de chaque ligne.

## Ce que tu devrais observer côté dashboard

- Des pics réguliers sur `system.disk.io.write_bytes` / `read_bytes` toutes
  les 2 minutes.
- Le fichier `app.json` se remplit dès le premier cycle (INFO inclus) — tu
  peux vérifier immédiatement, via `docker exec` dans le conteneur agent,
  qu'il lit bien ce fichier (`tail -f /var/log/apps/batch-report-app/app.json`).
- Mais dans le **dashboard**, seuls les WARN/ERROR apparaîtront (l'agent
  filtre les INFO de son côté) : un WARN occasionnel si le disque se remplit,
  et un ERROR toutes les ~20 minutes (10 cycles × 2 min).
