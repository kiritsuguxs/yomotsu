# Extensões Oficiais do Yomotsu 🇧🇷

Esta branch é dedicada exclusivamente ao desenvolvimento, manutenção e compilação das **extensões do Yomotsu** (Animes e Mangás).

> [!IMPORTANT]
> **Isolamento de Branches:** Esta branch é 100% desacoplada da `main`. Nenhuma alteração nesta branch deve conter código do aplicativo Yomotsu ou ser mesclada na `main`. Todo o código de extensões reside em pastas independentes no padrão `extension-<nome>/`.

---

## 📦 Extensões Disponíveis

| Extensão | Tipo | Diretório | Workflow de Compilação |
| :--- | :---: | :--- | :--- |
| **Watch Anime Hentai** | Anime (PT-BR) | `extension-watchanimehentai/` | [build-watchanimehentai-extension.yml](.github/workflows/build-watchanimehentai-extension.yml) |
| **Anime Hentai** | Anime (PT-BR) | `extension-animeshentai/` | [build-animeshentai-extension.yml](.github/workflows/build-animeshentai-extension.yml) |
| **Saimo TV** | Anime (PT-BR) | `extension-saimotv/` | [build-saimotv-extension.yml](.github/workflows/build-saimotv-extension.yml) |
| **ToonLivre** | Mangá (PT-BR) | `extension-toonlivre/` | [build-toonlivre-extension.yml](.github/workflows/build-toonlivre-extension.yml) |
| **Blitz Toons** | Mangá (PT-BR) | `extension-blitztoons/` | [build-blitztoons-extension.yml](.github/workflows/build-blitztoons-extension.yml) |
| **Baozinho** | Mangá (PT-BR) | `extension-baozinho/` | [build-baozinho-extension.yml](.github/workflows/build-baozinho-extension.yml) |
| **NHentaiBR** | Mangá (PT-BR) | `extension-nhentaibr/` | [build-nhentaibr-extension.yml](.github/workflows/build-nhentaibr-extension.yml) |
| **Blackout Comics** | Mangá (PT-BR) | `extension-blackoutcomics/` | [build-blackoutcomics-extension.yml](.github/workflows/build-blackoutcomics-extension.yml) |
| **Project Nox** | Mangá (PT-BR) | `extension-projectnox/` | [build-projectnox-extension.yml](.github/workflows/build-projectnox-extension.yml) |

---

## 📖 Guia de Desenvolvimento de Extensões

Ao criar uma nova extensão ou atualizar uma existente, siga rigorosamente as diretrizes abaixo:

### 1. 🎨 Regra de Ouro dos Ícones e Logotipos
- **Nunca use ícones genéricos ou de outros sites:** A extensão deve usar a identidade visual oficial do site que está sendo indexado.
- **Formato Quadrado (1:1):** Extraia a logo oficial (ou favicon de alta qualidade) do site. Caso a logo seja horizontal, enquadre-a em um squircle (fundo arredondado) estilizado com as cores da identidade visual do site, garantindo excelente contraste tanto no tema escuro quanto no tema claro do Yomotsu.
- **Densidades Mipmap Obrigatórias:** Gere o arquivo `ic_launcher.png` em todas as 5 pastas de resolução com amostragem de alta qualidade (Lanczos):
  - `src/main/res/mipmap-mdpi/ic_launcher.png` (48 × 48 px)
  - `src/main/res/mipmap-hdpi/ic_launcher.png` (72 × 72 px)
  - `src/main/res/mipmap-xhdpi/ic_launcher.png` (96 × 96 px)
  - `src/main/res/mipmap-xxhdpi/ic_launcher.png` (144 × 144 px)
  - `src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192 × 192 px)

---

### 2. 🔢 Controle de Versão (`extVersionCode`)
No arquivo `build.gradle.kts`:
- **Nova extensão:** Inicialize sempre com `val extVersionCode = 1`.
- **Qualquer alteração ou correção (ícone, scraper, extractor):** É **obrigatório** incrementar o `extVersionCode` (`2`, `3`, etc.).
- *Por que?* O Android e o Yomotsu comparam o `versionCode` do pacote. Se você reinstalar um APK com o mesmo código de versão anterior, o Android ignora a atualização de recursos e os ícones/código antigos continuam aparecendo em cache.

---

### 3. 🏗️ Estrutura de Código (Anime vs. Mangá)

#### Para Extensões de Anime (Vídeo / Streaming):
- **Classe base:** Estenda `ParsedAnimeHttpSource()`.
- **Seletores principais:**
  - `popularAnimeSelector()` & `popularAnimeRequest(page)`
  - `latestUpdatesSelector()` & `latestUpdatesRequest(page)`
  - `searchAnimeSelector()` & `searchAnimeRequest(page, query, filters)`
  - `animeDetailsParse(document)` (capa, título, gêneros, sinopse, status)
  - `episodeListSelector()` & `episodeListParse(response)`
  - `hosterListParse(response)` (lista de servidores e streams de vídeo)
- **Extração de Vídeo:**
  - Se o player usar redirecionamento HTTP 302 (CDNs diretos em `.mp4` ou `.m3u8`), resolva a rota via requisição `HEAD` ou repasse para o player interno do Yomotsu com o cabeçalho `Referer` correto.
  - Se usar players como Blogger, empacote um extrator dedicado na pasta `extractors/`.

#### Para Extensões de Mangá (Leitor de Imagens):
- **Classe base:** Estenda `ParsedHttpSource()`.
- **Seletores principais:**
  - `popularMangaSelector()` & `latestUpdatesSelector()`
  - `mangaDetailsParse(document)`
  - `chapterListSelector()` & `chapterListParse(response)`
  - `pageListParse(document)` (lista de páginas com URLs de imagem)
  - `imageUrlParse(document)`

---

### 4. 🔗 Metadados do Manifesto & Deep Linking
O `AndroidManifest.xml` de cada extensão deve conter:
- As tags de recursos: `<uses-feature android:name="tachiyomi.animeextension" />` (ou `tachiyomi.extension`).
- Metadados `${appName}`, `${extClass}`, `${nsfw}` (valor `"1"` para conteúdo adulto, `"0"` para convencional).
- `DeepLinkActivity`: Registre os domínios do site no `intent-filter` (`android:host` e `android:pathPattern`) para que links clicados no navegador abram direto no Yomotsu.

---

### 5. 🤖 Template de Workflow no GitHub Actions
Cada extensão deve possuir seu workflow individual em `.github/workflows/build-<nome>-extension.yml`:

```yaml
name: Build <Nome> Extension

on:
  push:
    branches:
      - extensão
    paths:
      - 'extension-<nome>/**'
      - '.github/workflows/build-<nome>-extension.yml'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.5'

      - name: Prepare Keystore
        env:
          STOREFILEBASE64: ${{ secrets.STOREFILEBASE64 }}
        run: |
          if [ -n "$STOREFILEBASE64" ]; then
            mkdir -p "$RUNNER_TEMP"
            echo "$STOREFILEBASE64" | base64 -d > "$RUNNER_TEMP/signing.keystore"
          fi

      - name: Build APK
        working-directory: ./extension-<nome>
        env:
          STOREPASSWORD: ${{ secrets.STOREPASSWORD }}
          KEYALIAS: ${{ secrets.KEYALIAS }}
          KEYPASSWORD: ${{ secrets.KEYPASSWORD }}
        run: gradle assembleRelease

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: <nome>-extension-apk
          path: extension-<nome>/build/outputs/apk/release/*.apk
```

---

## 🛠️ Como Compilar e Testar

### Pelo GitHub Actions (Recomendado):
1. Acesse a aba **Actions** no repositório GitHub.
2. Selecione o workflow da extensão desejada no menu lateral esquerdo.
3. Clique em **Run workflow** selecionando a branch `extensão`.
4. Ao concluir, role a página até a seção **Artifacts** e baixe o arquivo `.zip` com o `.apk` pronto para instalar no seu Android.

### Localmente via Terminal:
```bash
cd extension-<nome>
gradle assembleRelease
```
O APK final será gerado em `extension-<nome>/build/outputs/apk/release/`.
