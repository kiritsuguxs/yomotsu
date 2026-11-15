# Preferências de Fluxo Git e Release do Yomotsu

Diretrizes permanentes solicitadas pelo mantenedor (`kiritsuguxs`):

## 1. Branches e Merge (Sem Pull Requests)
- Sempre desenvolver e corrigir em uma branch temporária (`temp/...`), validando via GitHub Actions (`Build & Test`) antes de integrar na `main`.
- **Não abrir nem perguntar se deve abrir Pull Request (PR).** O fluxo padrão do projeto é **merge direto na `main`** (preferencialmente `git merge --ff-only`), push para `origin/main` e remoção da branch temporária local e remota.

## 2. Fluxo de Release Oficial (`vX.Y.Z`)
Ao publicar uma nova versão oficial:
1. Atualizar `versionCode` (+1) e `versionName` em `app/build.gradle.kts`.
2. Adicionar seção `## X.Y.Z` no topo de `YOMOTSU_CHANGELOG.md` em formato bilíngue:
   - `🇧🇷 **Português (Brasil)**`
   - `---`
   - `🇺🇸 **English**`
3. Atualizar a versão nos templates `.github/ISSUE_TEMPLATE/1_request_feature.yml` e `2_report_issue.yml`.
4. Commitar na `main`, criar a tag `vX.Y.Z` correspondente, enviar `main` e a tag para o `origin` e acompanhar o workflow `Release oficial do Yomotsu` até a publicação dos APKs.
