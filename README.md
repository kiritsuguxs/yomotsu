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

### 1. 🎨 Regra de Ouro dos Ícones e Logotipos (Obrigatório para Qualquer Extensão)

> [!IMPORTANT]
> **Identidade Visual Oficial do Site:**
> Toda e qualquer extensão futura desenvolvida para o Yomotsu — seja ela de **Manhwa/Mangá** (leitura) ou de **Anime/+18/Hentai** (vídeo) — **DEVE OBRIGATORIAMENTE utilizar o logotipo ou ícone oficial do próprio site indexado**.
>
> ❌ **O que NUNCA fazer:**
> - Nunca reutilize ícones provisórios de outras extensões (ex: nunca copiar o círculo rosa com "H" de `extension-animeshentai` ou o ícone de livro de `extension-toonlivre`).
> - Nunca use ícones genéricos ou inventados.
>
> ✔️ **Como extrair e aplicar a identidade visual oficial do site:**
> 1. **Favicon e App Icon:** Inspecione o HTML do site e verifique as tags `<link rel="icon">`, `<link rel="apple-touch-icon">` ou os arquivos `/favicon.png` e `/favicon.ico` na raiz. A maioria dos sites modernos já possui ícones quadrados em alta resolução (192x192 ou 256x256).
> 2. **Logo do Cabeçalho:** Inspecione a tag `<header>` / `<div class="logo">` do site para extrair a logo oficial em alta resolução (`.png` ou `.svg`).
> 3. **Composição em Squircle (1:1):** Como os ícones de aplicativos e extensões no Android são exibidos em formato quadrado ou circular, quando a logo do site for horizontal tipo banner, monte uma composição profissional em um *squircle* (retângulo arredondado com fundo escuro e contorno néon na cor de destaque do site), combinando o mascote/ícone do site com a logo para garantir legibilidade perfeita tanto no modo escuro quanto no modo claro do Yomotsu.
> 4. **Geração das 5 Densidades Mipmap Obrigatórias:**
>    Gere o arquivo `ic_launcher.png` em todas as 5 pastas de resolução utilizando amostragem Lanczos:
>    - `src/main/res/mipmap-mdpi/ic_launcher.png` (48 × 48 px)
>    - `src/main/res/mipmap-hdpi/ic_launcher.png` (72 × 72 px)
>    - `src/main/res/mipmap-xhdpi/ic_launcher.png` (96 × 96 px)
>    - `src/main/res/mipmap-xxhdpi/ic_launcher.png` (144 × 144 px)
>    - `src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192 × 192 px)
> 5. **Configuração no `AndroidManifest.xml`:**
>    Configure ambos os atributos no nó `<application>` para total compatibilidade com todos os launchers do Android:
>    ```xml
>    android:icon="@mipmap/ic_launcher"
>    android:roundIcon="@mipmap/ic_launcher"
>    ```

---

### 2. 🔢 Controle de Versão, `libVersion` e Cache do Android

> [!WARNING]
> O Android e o Instalador de Pacotes do sistema realizam **cache agressivo** do ícone e metadados dos APKs por nome de pacote (`applicationId`).

#### Regra Obrigatória da `libVersion` (Anime vs. Mangá):
- **Extensões de Anime (Vídeo / Streaming no Yomotsu, Anikku e Aniyomi):**
  - **DEVE utilizar:** `val libVersion = "14"`
  - `calculatedVersionCode = 140000 + extVersionCode`
  - `versionName = "$libVersion.$extVersionCode"` (ex: `14.5`)
  - *Por que?* Forks focados em anime como o **Anikku** exigem estritamente `LIB_VERSION_MIN = 12` e `LIB_VERSION_MAX = 16` no seu `ExtensionLoader`. Se uma extensão de anime usar `1.6`, o Anikku rejeita o pacote com `LoadResult.Error` e **a extensão desaparece completamente após ser confiada**! O Yomotsu aceita `libVersion = 14` nativamente (`if (isAnime) true`).
  - **Minificação e Otimização de Tamanho:** Defina `isMinifyEnabled = true` no `buildTypes.release` com a regra `-keep class eu.kanade.tachiyomi.animeextension.** { *; }` em `proguard-rules.pro`. Isso ativa a poda de código morto (*tree-shaking*) das dependências do Kotlin, reduzindo o tamanho do APK de ~900 KB para **~140 KB**, sem quebrar o carregamento dinâmico.
- **Extensões de Mangá (Leitura no Yomotsu, Mihon e Tachiyomi):**
  - Utilizam `val libVersion = "1.6"`.

#### Controle de Versão (`extVersionCode`):
- **Nova extensão criada:** Inicialize com `val extVersionCode = 1`.
- **Qualquer alteração ou correção (ícone, scraper, extrator, seletor):** É **terminantemente obrigatório** incrementar o `extVersionCode` (`2`, `3`, `4`, `5`, `6`, etc.).
- **Se o ícone antigo continuar aparecendo após instalar:**
  1. Desinstale a versão antiga da extensão no seu Android antes de instalar a nova (Configurações > Aplicativos > Extensão > Desinstalar).
  2. Apague o arquivo `.apk` antigo que estava na sua pasta `Download/` para evitar que o gerenciador de arquivos do celular abra o APK de cache anterior.
  3. No workflow do GitHub Actions, configure o nome do artefato e do arquivo final com o número da versão (ex: `watchanimehentai-v6-apk` e `watchanimehentai-v6.apk`) para impossibilitar colisões de arquivos no celular do usuário.

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

### 4. 🔗 Metadados do Manifesto & Isolamento de Forks
O `AndroidManifest.xml` de cada extensão deve conter **apenas** a feature correspondente:
- **Para Extensões de Anime:**
  - Use **apenas** `<uses-feature android:name="tachiyomi.animeextension" />`.
  - ❌ **NUNCA inclua `tachiyomi.extension`:** Isso garante que aplicativos exclusivos de mangá (Mihon, Tachiyomi original, TachiyomiJ2K) **ignorem o APK completamente**, e a extensão apareça somente em forks com suporte a anime (Yomotsu, Anikku, Aniyomi, Animiru).
  - Use metadados: `tachiyomi.animeextension.class`, `tachiyomi.animeextension.factory`, `tachiyomi.animeextension.nsfw`, `tachiyomi.animeextension.libVersion`.
- **Para Extensões de Mangá:**
  - Use `<uses-feature android:name="tachiyomi.extension" />` e metadados `tachiyomi.extension.*`.
- **DeepLinkActivity:** Registre os domínios do site no `intent-filter` (`android:host` e `android:pathPattern`) para que links clicados no navegador abram direto no Yomotsu.

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
