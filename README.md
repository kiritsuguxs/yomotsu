# Extensões Oficiais do Yomotsu 🇧🇷

Esta branch é dedicada exclusivamente ao desenvolvimento e compilação das **extensões do Yomotsu**.

Cada pasta de extensão é um projeto Android totalmente independente e possui seu próprio workflow de compilação automática no GitHub Actions.

---

## 📦 Extensões Disponíveis

| Extensão | Diretório | Workflow de Compilação |
| :--- | :--- | :--- |
| **ToonLivre** | `extension-toonlivre/` | `.github/workflows/build-toonlivre-extension.yml` |
| **Blitz Toons** | `extension-blitztoons/` | `.github/workflows/build-blitztoons-extension.yml` |
| **Baozinho** | `extension-baozinho/` | `.github/workflows/build-baozinho-extension.yml` |
| **NHentaiBR** | `extension-nhentaibr/` | `.github/workflows/build-nhentaibr-extension.yml` |
| **Saimo TV** | `extension-saimotv/` | `.github/workflows/build-saimotv-extension.yml` |
| **Blackout Comics** | `extension-blackoutcomics/` | `.github/workflows/build-blackoutcomics-extension.yml` |
| **Project Nox** | `extension-projectnox/` | `.github/workflows/build-projectnox-extension.yml` |
| **Anime Hentai** | `extension-animeshentai/` | `.github/workflows/build-animeshentai-extension.yml` |

---

## 🛠️ Como Compilar

### Via GitHub Actions (Recomendado):
1. Vá até a aba **Actions** no GitHub.
2. Selecione o workflow da extensão desejada (ex: `Build ToonLivre Extension`).
3. Clique em **Run workflow** selecionando a branch `extensão`.
4. O APK assinado e pronto para instalar será disponibilizado nos **Artifacts**.

### Localmente via Terminal:
Para compilar uma extensão individualmente:
```bash
cd extension-toonlivre
gradle assembleRelease
```
O APK de release será gerado em `extension-<nome>/build/outputs/apk/release/`.
