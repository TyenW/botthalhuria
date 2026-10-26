# 🧱 Guia do Template (`server-template.json`)

O template é **a fonte da verdade** do servidor. Tudo que o bot cria — cargos, categorias, canais, permissões, painéis, boas-vindas, tickets e salas de voz — sai desse arquivo. Edite, rode `/sync-server` e o Discord se ajusta.

> 💡 Abra o arquivo no VS Code: graças ao `"$schema"` na primeira linha, você ganha **autocomplete**, descrição de cada campo e sublinhado vermelho em valores inválidos (ex.: uma permissão escrita errado).

---

## Sumário

1. [Regra de ouro: as `key`s](#1-regra-de-ouro-as-keys)
2. [Estrutura geral](#2-estrutura-geral)
3. [`branding` — identidade visual](#3-branding--identidade-visual)
4. [`roles` — cargos](#4-roles--cargos)
5. [`categories` — categorias](#5-categories--categorias)
6. [`channels` — canais](#6-channels--canais)
7. [`panel` — painéis](#7-panel--painéis)
8. [`onboarding` — entrada de membros](#8-onboarding--entrada-de-membros)
9. [`tickets` — atendimento](#9-tickets--atendimento)
10. [Placeholders](#10-placeholders)
11. [Como as permissões funcionam](#11-como-as-permissões-funcionam)
12. [Receitas rápidas](#12-receitas-rápidas)
13. [Validação e erros](#13-validação-e-erros)
14. [`leveling` — níveis e XP](#14-leveling--níveis-e-xp)
15. [`economy` — moedas e loja](#15-economy--moedas-e-loja)
16. [`moderation` — moderação e AutoMod](#16-moderation--moderação-e-automod)
17. [`welcomeCard` — cartão de boas-vindas](#17-welcomecard--cartão-de-boas-vindas)
18. [`events` — eventos agendados](#18-events--eventos-agendados)
19. [`messages` — textos do bot](#19-messages--textos-do-bot)
20. [Template por servidor e backup](#20-template-por-servidor-e-backup)
21. [`invites` — rastreio de convites](#21-invites--rastreio-de-convites)

---

## 1. Regra de ouro: as `key`s

Todo cargo, categoria e canal tem uma `key` (ex.: `ROLE_MEMBER`, `CHAN_REGRAS`). Na primeira execução o bot salva no banco **qual ID do Discord pertence a cada key**. A partir daí:

| Você muda… | O que acontece no `/sync-server` |
| :--- | :--- |
| o `name` | O item existente é **renomeado**. |
| a categoria de um canal | O canal é **movido** para a nova categoria. |
| a ordem na lista | Cargos/canais são **reordenados**. |
| a `key` | O bot acha que é um item novo e **cria outro** (o antigo fica órfão). ⚠️ |

**Nunca renomeie uma `key` depois do primeiro `/setup-server`.** Para mudar o visual, mude o `name`.

---

## 2. Estrutura geral

```jsonc
{
  "$schema": "./server-template.schema.json",
  "templateName": "Thalhuria • Reino da Resenha",
  "version": "4.0.0",
  "branding":    { ... },  // cores, rodapé, status do bot
  "onboarding":  { ... },  // cargo automático, boas-vindas, verificação
  "welcomeCard": { ... },  // cartão de boas-vindas em imagem
  "tickets":     { ... },  // tickets, tipos e transcrição
  "leveling":    { ... },  // XP, níveis e recompensas
  "economy":     { ... },  // moedas, /daily e loja
  "moderation":  { ... },  // logs de moderação, advertências e AutoMod
  "events":      { ... },  // eventos agendados
  "messages":    { ... },  // textos do bot sobrescritos
  "roles":       [ ... ],  // cargos (ordem = hierarquia)
  "categories":  [ ... ]   // categorias, cada uma com seus canais
}
```

Todas as seções de configuração são opcionais: se omitidas, usam valores padrão. Recursos que dependem de um canal/cargo (ex.: anúncios de nível) simplesmente não fazem nada se a key não for configurada.

---

## 3. `branding` — identidade visual

| Campo | Padrão | Descrição |
| :--- | :--- | :--- |
| `name` | `Thalhuria` | Nome da marca, usado no placeholder `{brand}`. |
| `tagline` | — | Frase de efeito (reservado para usos futuros). |
| `colorHex` | `#8B5CF6` | Cor padrão de todos os embeds do bot. |
| `footer` | `Thalhuria` | Texto do rodapé dos embeds. |
| `iconUrl` | ícone do servidor | Ícone do rodapé. |
| `activity` | — | Status do bot: aparece como **"Assistindo _activity_"**. Atualizado ao iniciar e a cada `/sync-server`. |

---

## 4. `roles` — cargos

**A ordem da lista é a hierarquia**: o primeiro cargo fica mais alto. O bot posiciona todos logo abaixo do próprio cargo dele.

```json
{
  "key": "ROLE_MEMBER",
  "name": "✨ │ Cidadão",
  "colorHex": "#8B5CF6",
  "hoist": true,
  "mentionable": false,
  "memberRole": true,
  "permissions": ["VIEW_CHANNEL", "MESSAGE_SEND"]
}
```

| Campo | Descrição |
| :--- | :--- |
| `colorHex` | Cor do nome. Omitir/`null` = sem cor. |
| `hoist` | Exibe os membros separados na lista lateral. |
| `mentionable` | Qualquer um pode `@marcar` o cargo. Ligue em cargos de notificação. |
| `ownerRole` | Cargo do **dono**: dado automaticamente ao dono do servidor em todo setup/sync. No máximo 1. |
| `adminRole` | Cargo da administração (ganha acesso às salas de `/membro configurar`). |
| `memberRole` | Cargo dado por `/membro configurar`. |
| `permissions` | Permissões globais. Aceita nomes do JDA (`MESSAGE_SEND`) **ou** da API do Discord (`SEND_MESSAGES`). |
| `selfAssignable` | Aparece no **menu de cargos** (painel `SELF_ROLES`). Máx. 25. |
| `emoji` | Emoji do cargo no menu. |
| `description` | Texto curto exibido no menu (até 100 caracteres). |

### Cargos do template atual (de cima para baixo)

| Key | Nome | Papel |
| :--- | :--- | :--- |
| `ROLE_OWNER` | 👑 Soberano de Thalhuria | **Dono** — dado automaticamente |
| `ROLE_ADMIN` | ⚜️ Alto Conselho | **Administradores** |
| `ROLE_MODERATOR` | 🛡️ Guardião Real | Moderação (ban, kick, castigo, mensagens, voz, eventos) |
| `ROLE_SUPPORT` | 🧭 Guia do Reino | Suporte e tickets |
| `ROLE_EVENTS` | 🎪 Arauto dos Eventos | Organiza eventos e conduz o palco |
| `ROLE_PARTNER` | 🤝 Embaixador | Parceiros (isentos do bloqueio de convites) |
| `ROLE_VIP` | 💎 Mecenas | Apoiadores/boosters (emojis externos) |
| `ROLE_BOT` | ⚙️ Autômato | Dado automaticamente a todo bot |
| `ROLE_COLOR_*` / `ROLE_TITLE_BARD` | 🌟 🌹 🌊 🌿 🌑 🎻 | Itens da loja (cores e título) |
| `ROLE_LVL_50` … `ROLE_LVL_5` | 🐉 🏰 ⚔️ 🗡️ | Recompensas de nível |
| `ROLE_MEMBER` | ✨ Cidadão | Membro verificado |
| `ROLE_VISITOR` | 🌱 Viajante | Quem acabou de entrar (só vê o Portal) |
| `ROLE_PING_NEWS` / `ROLE_PING_EVENTS` | 🔔 Avisos / 🎉 Eventos | Notificações opcionais |
| `ROLE_INTEREST_*` | 🎮 🎨 🎵 🎬 | Interesses (menu de cargos) |

**Por que nessa ordem?** O Discord pinta o nome com a cor do cargo **mais alto** que tem cor. Por isso as cores da loja ficam acima dos níveis (quem compra uma cor a vê), e os níveis acima do ✨ Cidadão (a cor muda conforme o membro evolui).

> ⚠️ **Staff também precisa do cargo de membro** (`✨ Cidadão`) para ver os canais da comunidade — as categorias liberam acesso por esse cargo.

---

## 5. `categories` — categorias

```json
{
  "key": "CAT_COMMUNITY",
  "name": "━━ 💬 PRAÇA CENTRAL ━━",
  "position": 2,
  "individualRoomsCategory": false,
  "permissionOverrides": [ ... ],
  "channels": [ ... ]
}
```

| Campo | Descrição |
| :--- | :--- |
| `position` | Ordem da categoria. `0` = usa a posição na lista. |
| `individualRoomsCategory` | Onde `/membro configurar` cria as salas privadas (no máximo 1). |
| `permissionOverrides` | Regras da categoria; os canais herdam (veja [§11](#11-como-as-permissões-funcionam)). |
| `channels` | **A ordem da lista é a ordem dos canais.** |

---

## 6. `channels` — canais

```json
{
  "key": "CHAN_SUGGESTIONS",
  "name": "💡・sugestões",
  "type": "TEXT",
  "topic": "Uma ideia por mensagem.",
  "slowmode": 120,
  "autoReactions": ["✅", "❌"],
  "autoThread": true
}
```

| Campo | Tipo de canal | Descrição |
| :--- | :--- | :--- |
| `type` | — | `TEXT`, `VOICE` ou `ANNOUNCEMENT`. Anúncios exigem **Comunidade** ativada no servidor; sem ela o bot cria um canal de texto comum. |
| `topic` | texto | Descrição do canal. Aceita [placeholders](#10-placeholders). |
| `systemLogChannel` | texto | Recebe os logs de auditoria. **Exatamente 1** no template. |
| `slowmode` | texto | Segundos entre mensagens (0–21600). |
| `nsfw` | todos | Restrição de idade. |
| `userLimit` | voz | Máximo de pessoas (0 = ilimitado). |
| `joinToCreate` | voz | Vira um canal **"entre para criar"** (veja abaixo). |
| `tempChannelName` | voz | Nome da sala criada. `{user}` = nome do membro. |
| `tempUserLimit` | voz | Limite inicial da sala criada. |
| `autoReactions` | texto | Reações automáticas em toda mensagem de membro. |
| `autoThread` | texto | Abre um tópico de discussão para cada mensagem. |
| `counter` | voz | Canal **contador**: `MEMBERS`, `HUMANS`, `BOTS`, `ONLINE` ou `BOOSTS`. O `name` precisa conter `{value}` (ex.: `"👥・Habitantes: {value}"`). Atualizado a cada 10 min. |
| `permissionOverrides` | todos | Regras **por cima** das da categoria. |
| `panel` | texto | Mensagem fixa gerenciada pelo bot ([§7](#7-panel--painéis)). |

> 📝 Canais de texto: o Discord converte o nome para minúsculas e troca espaços por hífens. O bot já compara assim, então não há renomeações desnecessárias.

### Salas de voz temporárias (`joinToCreate`)

1. O membro entra em **➕・Criar Sala**.
2. O bot cria **🔊・Salão de Fulano** na mesma categoria e move o membro para lá.
3. O dono pode renomear, limitar, mover e silenciar pessoas na própria sala.
4. Quando a sala esvazia, ela é apagada. Se o bot reiniciar, salas vazias são limpas na inicialização.
5. Cada membro tem no máximo uma sala; entrar no hub de novo leva de volta para ela.
6. No chat da sala chega um **painel de controle** que só o dono usa: 🔒 trancar/destrancar · 👁️ ocultar/mostrar · ✏️ renomear · 👥 limite · 👑 transferir para quem está na sala.

---

## 7. `panel` — painéis

Um painel é uma mensagem que o bot **publica uma vez e depois edita** a cada `/sync-server` (o ID fica salvo no banco). Se alguém apagar a mensagem, o próximo sync publica de novo.

```json
"panel": {
  "type": "VERIFY",
  "title": "✅ Atravesse os Portões",
  "description": "Leu as leis em {channel:CHAN_REGRAS}? Clique abaixo!",
  "colorHex": "#22C55E",
  "buttonLabel": "Entrar no reino",
  "buttonEmoji": "🗝️",
  "fields": [ { "name": "Título", "value": "Texto", "inline": false } ],
  "imageUrl": "https://…/banner.png",
  "thumbnailUrl": null
}
```

| `type` | O que adiciona |
| :--- | :--- |
| `EMBED` | Só o embed (regras, FAQ, guias). |
| `VERIFY` | Botão que dá o cargo `onboarding.verifiedRoleKey` e remove o `autoRoleKey`. |
| `SELF_ROLES` | Menu com todos os cargos `selfAssignable`. Marcar adiciona, desmarcar remove. A lista de cargos é anexada ao embed automaticamente. |
| `TICKET` | Botão que abre um ticket privado. |

Limites do Discord (o validador avisa): título 256, descrição 4096, até 25 fields, valor do field 1024, texto do botão 80.

### Vários botões no mesmo painel (`buttons`)

```json
"buttons": [
  { "label": "Suporte",  "emoji": "🧭", "style": "PRIMARY", "action": "TICKET", "ticketType": "suporte" },
  { "label": "Denúncia", "emoji": "🚨", "style": "DANGER",  "action": "TICKET", "ticketType": "denuncia" },
  { "label": "Quero ser avisado", "emoji": "🔔", "action": "ROLE", "roleKey": "ROLE_PING_NEWS" },
  { "label": "Nosso site", "action": "URL", "url": "https://exemplo.com" }
]
```

| `action` | O que faz |
| :--- | :--- |
| `TICKET` | Abre um ticket do tipo `ticketType` (veja [§9](#9-tickets--atendimento)). Sem `ticketType` = ticket padrão. |
| `VERIFY` | Mesmo efeito do painel `VERIFY`. |
| `ROLE` | Alterna o cargo `roleKey`: dá se o membro não tem, tira se já tem. |
| `URL` | Botão de link. |

`style`: `PRIMARY` (azul), `SECONDARY` (cinza), `SUCCESS` (verde), `DANGER` (vermelho). Máximo de 5 botões. Quando `buttons` existe, ele substitui o botão padrão do tipo (o menu do `SELF_ROLES` continua).

---

## 8. `onboarding` — entrada de membros

Fluxo completo do template atual:

```
Membro entra ──► recebe 🌱 Viajante ──► mensagem em 👋・boas-vindas
     │                 (só vê o Portal)
     ▼
Lê 📜・regras ──► clica "Entrar no reino" em ✅・verificação
     │
     ▼
Perde 🌱 Viajante, ganha ✨ Cidadão ──► todos os canais liberados
     │
     ▼
Escolhe interesses em 🎭・cargos
```

| Campo | Descrição |
| :--- | :--- |
| `enabled` | Liga/desliga tudo isso. |
| `autoRoleKey` | Cargo dado a quem entra. |
| `botRoleKey` | Cargo dado a bots (na entrada e em todo `/sync-server`). |
| `verifiedRoleKey` | Cargo do botão de verificação. |
| `removeAutoRoleOnVerify` | Remove o `autoRoleKey` ao verificar. |
| `welcomeChannelKey` | Canal da mensagem de boas-vindas. |
| `pingOnWelcome` | Marca o membro junto com o embed. |
| `welcomeTitle` / `welcomeMessage` | Textos. Aceitam `{user}`, `{username}`, `{count}` e os placeholders gerais. |
| `welcomeImageUrl` | Banner no embed de boas-vindas. |
| `verifiedMessage` | Resposta privada após verificar. |
| `dmWelcome` | Envia também uma **mensagem privada** de boas-vindas (ignorado se o membro bloqueia DMs). |
| `dmTitle` / `dmMessage` | Título e texto da DM. Aceitam `{user}`, `{username}`, `{count}` e os placeholders gerais. |
| `dmButtonChannelKey` | Canal aberto pelo botão da DM (ex.: `CHAN_VERIFY`). |
| `rulesChannelKey` | Servidores com Comunidade: o **canal de regras oficial** do Discord assume este canal do template (é renomeado, movido, recebe permissões e painel). Nada é duplicado. |
| `communityUpdatesChannelKey` | Servidores com Comunidade: o canal de **atualizações da Comunidade** assume este canal do template (use um canal da staff). |

> Membros que **já estavam** no servidor antes do setup não recebem o cargo automático; eles só precisam clicar no botão de verificação.

---

## 9. `tickets` — atendimento

| Campo | Descrição |
| :--- | :--- |
| `categoryKey` | Categoria onde os tickets nascem (padrão: a `individualRoomsCategory`). |
| `staffRoleKeys` | Cargos que veem, são marcados e podem fechar tickets. |
| `channelPrefix` | Prefixo do nome do canal (`🎫・fulano`). |
| `openTitle` / `openMessage` | Mensagem inicial dentro do ticket. |
| `closeDelaySeconds` | Espera antes de apagar o canal ao fechar. |

| `transcripts` | Ao fechar, gera um **HTML** com toda a conversa (visual do Discord) e envia ao canal de logs. |
| `dmTranscript` | Também envia a transcrição na DM de quem abriu. |
| `types` | Tipos de ticket, usados pelos botões `TICKET` dos painéis. |

### Tipos de ticket

```json
{
  "key": "denuncia",
  "label": "Denúncia",
  "emoji": "🚨",
  "channelPrefix": "🚨・",
  "staffRoleKeys": ["ROLE_MODERATOR"],
  "openMessage": "Sua denúncia é sigilosa. Informe quem, onde, quando e prints."
}
```

Cada tipo tem prefixo, equipe e mensagem próprios. Sem `staffRoleKeys`, vale o `staffRoleKeys` geral.

Regras: 1 ticket aberto por membro (de qualquer tipo); fecha quem abriu, a equipe do tipo ou administradores; abertura, fechamento e transcrição vão para o canal de logs.

---

## 10. Placeholders

| Placeholder | Onde | Vira |
| :--- | :--- | :--- |
| `{server}` | todos os textos | Nome do servidor |
| `{brand}` | todos os textos | `branding.name` |
| `{channel:KEY}` | todos os textos | Menção clicável ao canal (`#regras`) |
| `{role:KEY}` | todos os textos | Menção ao cargo (`@Cidadão`) |
| `{user}` | boas-vindas, verificação, tickets | Menção ao membro |
| `{username}` | boas-vindas, verificação, tickets | Nome do membro |
| `{count}` | boas-vindas | Número de membros |
| `{level}` | anúncio de nível | Nível alcançado |
| `{currency}` / `{coin}` | todos os textos | Nome e emoji da moeda |

"Todos os textos" = tópicos de canais, painéis, boas-vindas e tickets. Se a key não existir, o validador avisa e o texto mostra `` `#key` `` no lugar.

---

## 11. Como as permissões funcionam

```json
{ "target": "ROLE_MEMBER", "allow": ["VIEW_CHANNEL"], "deny": ["MESSAGE_SEND"] }
```

- `target`: `@everyone`, a **key** de um cargo (recomendado) ou o nome exato de um cargo.
- **Categoria**: as regras são aplicadas na categoria.
- **Canal sem `permissionOverrides`**: fica **sincronizado** com a categoria.
- **Canal com `permissionOverrides`**: permissões da categoria **+** as regras do canal por cima (mesmo `target` = a do canal vence).
- Permissões só são reenviadas ao Discord quando diferem do desejado, então o sync é rápido.

> ⚠️ O template manda. Ajustes de permissão feitos **à mão** em canais do template são desfeitos no próximo `/sync-server`. Mude no JSON.

O padrão de acesso do template atual:

| Categoria | `@everyone` / 🌱 Viajante | ✨ Cidadão | Staff |
| :--- | :---: | :---: | :---: |
| 🏛️ Portal | 👁️ leitura | 👁️ leitura | ✅ |
| 💬 Praça Central | ❌ | ✅ | ✅ (com cargo de membro) |
| 🔊 Salões de Voz | ❌ | ✅ | ✅ (com cargo de membro) |
| 🎫 Suporte | ❌ | 👁️ leitura | ✅ |
| 🔒 Conselho | ❌ | ❌ | ✅ |
| ⚙️ Arquivos (logs) | ❌ | ❌ | 👁️ Guardiões |
| 🎟️ Atendimento | ❌ | só o próprio ticket | ✅ |

---

## 12. Receitas rápidas

**Adicionar um canal novo**
```json
{ "key": "CHAN_ANIME", "name": "🍥・animes", "type": "TEXT", "topic": "Fala de anime aqui" }
```
Cole dentro de `channels` da categoria desejada e rode `/sync-server`.

**Novo cargo no menu de cargos**
```json
{ "key": "ROLE_INTEREST_ANIME", "name": "🍥 Otaku", "emoji": "🍥",
  "description": "Animes e mangás", "selfAssignable": true, "permissions": [] }
```
Adicione em `roles` (no fim da lista) e rode `/sync-server` — o menu é atualizado sozinho.

**Canal de votação**
```json
"autoReactions": ["👍", "👎"], "autoThread": false
```

**Sala de voz para 5 pessoas**
```json
{ "key": "CHAN_VOICE_SQUAD", "name": "🎯・Squad", "type": "VOICE", "userLimit": 5 }
```

**Canal só para quem tem um cargo**
```json
"permissionOverrides": [
  { "target": "@everyone", "deny": ["VIEW_CHANNEL"] },
  { "target": "ROLE_MEMBER", "deny": ["VIEW_CHANNEL"] },
  { "target": "ROLE_INTEREST_ART", "allow": ["VIEW_CHANNEL", "MESSAGE_SEND"] }
]
```

**Desligar a verificação** (todo mundo entra direto como membro)
```json
"onboarding": { "autoRoleKey": "ROLE_MEMBER", "verifiedRoleKey": null, ... }
```
…e remova o canal `CHAN_VERIFY`.

---

## 13. Validação e erros

O template é validado em três momentos:

1. **No editor** — pelo JSON Schema (`server-template.schema.json`).
2. **No `mvn test`** — `TemplateValidationTest` falha se o template tiver qualquer problema.
3. **No Discord** — `/setup-server`, `/sync-server` e `/server-status` listam os avisos.

O que é verificado: keys duplicadas, referências para keys inexistentes (em permissões, onboarding, tickets e placeholders), permissões inválidas, cores inválidas, limites do Discord, tipos de canal e combinações impossíveis (ex.: `joinToCreate` em canal de texto, painel em canal de voz).

**JSON quebrado** (vírgula faltando, campo com nome errado): o comando responde com o erro e **não altera nada** no servidor. Antes dessa versão, o bot caía silenciosamente no template embutido — e aplicava a estrutura errada.

---

## 14. `leveling` — níveis e XP

```json
"leveling": {
  "enabled": true,
  "xpMin": 15, "xpMax": 25, "cooldownSeconds": 60,
  "xpPerVoiceMinute": 10,
  "announceChannelKey": "CHAN_LEVELUPS",
  "levelUpMessage": "⚔️ {user} subiu para o **nível {level}**!",
  "stackRewards": false,
  "rewards": [ { "level": 5, "roleKey": "ROLE_LVL_5" }, { "level": 15, "roleKey": "ROLE_LVL_15" } ],
  "ignoredChannelKeys": ["CHAN_COMMANDS"]
}
```

| Campo | Descrição |
| :--- | :--- |
| `xpMin` / `xpMax` | XP aleatório por mensagem. |
| `cooldownSeconds` | Só a primeira mensagem a cada N segundos dá XP (evita farm por flood). |
| `xpPerVoiceMinute` | XP por minuto em call **com pelo menos mais uma pessoa**, sem estar ensurdecido e fora do canal AFK. |
| `announceChannelKey` | Onde anunciar subidas de nível. Vazio = no canal da mensagem. |
| `stackRewards` | `false` = o membro fica só com o cargo do maior nível (recomendado para níveis com cor). |
| `ignoredChannelKeys` | Canais que não dão XP. |

**Curva de níveis:** subir do nível *n* para *n+1* custa `5n² + 50n + 100` XP XP total acumulado: nível 1 = 100 · 5 = 1.150 · 15 = 11.825 · 30 = 67.525 · 50 = 268.375. Com o padrão (~20 XP/min conversando e 10 XP/min em call), o nível 5 sai no primeiro dia ativo e o 50 é para os veteranos de verdade.

---

## 15. `economy` — moedas e loja

```json
"economy": {
  "enabled": true,
  "currencyName": "Thalers", "currencyEmoji": "🪙",
  "coinsPerMessage": 2, "coinsPerVoiceMinute": 1,
  "dailyAmount": 150, "dailyStreakBonus": 25, "maxStreakBonusDays": 7,
  "shop": [
    { "key": "cor_rubi", "name": "Cor Rubi", "emoji": "🌹", "roleKey": "ROLE_COLOR_RUBY", "price": 1500, "group": "cor" }
  ]
}
```

- Moedas por mensagem seguem o mesmo cooldown do XP.
- `/daily` libera a cada 20h; a sequência continua se o membro voltar em até 48h. Bônus = `dailyStreakBonus × dias` (até `maxStreakBonusDays`).
- Itens com o mesmo `group` são exclusivos: comprar a Safira remove o Rubi. Comprar de novo um item que já tem não cobra.
- Os cargos da loja precisam existir em `roles` e ficar abaixo do cargo do bot.

---

## 16. `moderation` — moderação e AutoMod

```json
"moderation": {
  "modLogChannelKey": "CHAN_MODLOGS",
  "logMessageEdits": true, "logMessageDeletes": true, "logVoice": true,
  "warningExpiryDays": 30,
  "warnThresholds": [
    { "warnings": 3, "action": "TIMEOUT", "durationMinutes": 60 },
    { "warnings": 7, "action": "BAN" }
  ],
  "automod": {
    "enabled": true,
    "blockedWords": ["*free nitro*"],
    "blockInvites": true, "mentionLimit": 6, "antiSpam": true,
    "timeoutMinutes": 10,
    "blockMessage": "🚫 Mensagem bloqueada.",
    "exemptRoleKeys": ["ROLE_ADMIN", "ROLE_MODERATOR"]
  }
}
```

| Campo | Descrição |
| :--- | :--- |
| `modLogChannelKey` | Canal dos logs de moderação (sem ele, vai para o canal de sistema). |
| `logMessageDeletes` / `logMessageEdits` | Registrar mensagens apagadas/editadas. O **conteúdo** só aparece com `MESSAGE_CONTENT_INTENT=true` no `.env` (e a intent ativada no Developer Portal); mensagens anteriores à inicialização do bot não são conhecidas. |
| `logVoice` | Entradas, saídas e trocas de call. |
| `warningExpiryDays` | Advertências contam só por N dias (0 = para sempre). |
| `warnThresholds` | Punição automática ao atingir **exatamente** N advertências ativas: `TIMEOUT` (com `durationMinutes`, máx. 28 dias), `KICK` ou `BAN`. |

### AutoMod nativo
O bot cria regras de **AutoMod do próprio Discord** (prefixo `[Bot]`) a cada setup/sync. Elas funcionam mesmo com o bot offline e não consomem recursos dele. Regras criadas manualmente pela staff nunca são alteradas.

| Campo | Regra criada |
| :--- | :--- |
| `blockedWords` | Bloqueio de palavras/expressões. `*` é curinga (`*nitro*` pega "freenitro"). |
| `blockInvites` | Bloqueio de convites de outros servidores. |
| `mentionLimit` | Bloqueio de mensagens com mais de N menções + proteção contra raid de menções. |
| `antiSpam` | Detecção de spam do Discord. |
| `timeoutMinutes` | Castigo automático para quem dispara as regras (não vale para spam, limitação do Discord). |
| `exemptRoleKeys` | Cargos isentos. |

Alertas vão para o `modLogChannelKey`.

---

## 17. `welcomeCard` — cartão de boas-vindas

```json
"welcomeCard": {
  "enabled": true,
  "backgroundColorHex": "#1E1B2E",
  "accentColorHex": null,
  "backgroundImageUrl": null,
  "title": "BEM-VINDO(A) A THALHURIA",
  "subtitle": "Habitante nº {count} do reino"
}
```

![Cartão de boas-vindas](img/cartao-boas-vindas.png)

Imagem 1200×420: fundo em degradê com brilhos e partículas (ou `backgroundImageUrl` escurecida), avatar com glow e anel na cor de destaque (padrão: `branding.colorHex`), título em letras espaçadas, nome em destaque, selo com o `subtitle` e `branding.footer` no canto. As partículas usam o ID do membro, então cada cartão é único. Emojis são removidos dos textos da imagem (Java2D não desenha emoji). Se a imagem falhar, a mensagem sai sem ela.

O **cartão do `/rank`** usa o mesmo fundo (`backgroundColorHex`/`backgroundImageUrl`) com o anel na cor do cargo do membro:

![Cartão de rank](img/cartao-rank.png)

---

## 18. `events` — eventos agendados

```json
"events": {
  "enabled": true,
  "announceChannelKey": "CHAN_EVENTS",
  "pingRoleKey": "ROLE_PING_EVENTS",
  "reminderMinutes": 15,
  "timezone": "America/Sao_Paulo"
}
```

`/evento criar nome:"Noite de Valorant" inicio:"25/12 20:00" duracao:120 local:"Arena 01"` cria o **evento nativo** do Discord (aparece no topo do servidor com botão "Interessado"), anuncia em `announceChannelKey` marcando `pingRoleKey` e envia um lembrete `reminderMinutes` antes. Os lembretes são reagendados se o bot reiniciar.

---

## 19. `messages` — textos do bot

Todos os textos que o bot responde ficam em [`src/main/resources/messages.properties`](../src/main/resources/messages.properties). Para mudar o tom sem recompilar, sobrescreva qualquer chave no template:

```json
"messages": {
  "economy.daily.ok": "💰 O tesouro real te concedeu {amount}! Sequência: {streak} dias.",
  "ticket.close.button": "Encerrar atendimento"
}
```

Os placeholders disponíveis estão comentados acima de cada chave no `.properties`.

---

## 20. Template por servidor e backup

- **Template por servidor:** crie `templates/<ID_DO_SERVIDOR>.json`. Aquele servidor passa a usar esse arquivo; os demais continuam com o `server-template.json`. O `/server-status` e os comandos de setup/sync usam automaticamente o template certo.
- **Backup:** `/backup` gera um template com o estado atual do servidor (cargos, cores, permissões, categorias, canais, tópicos, slowmode, limites) e envia o arquivo na resposta, além de salvar em `backups/`. Itens criados pelo bot mantêm a `key` original e as seções de configuração/painéis são copiadas do template ativo.
- **Restaurar ou clonar:** copie o backup para `templates/<ID>.json` (do mesmo ou de outro servidor) e rode `/setup-server`.
- **Simular antes de aplicar:** `/sync-server simular:true` lista cargos/categorias/canais que seriam criados, renomeados, movidos ou ajustados.

---

## 21. `invites` — rastreio de convites

```json
"invites": {
  "enabled": true,
  "showInWelcome": true,
  "coinsPerInvite": 250,
  "rewardOnlyVerified": true,
  "minAccountAgeDays": 7,
  "removeTierOnLeave": true,
  "tiers": [
    { "invites": 3, "roleKey": "ROLE_RECRUITER_1" },
    { "invites": 10, "roleKey": "ROLE_RECRUITER_2" },
    { "invites": 25, "roleKey": "ROLE_RECRUITER_3" }
  ]
}
```

**Como o bot descobre o convite:** o Discord não informa qual convite alguém usou. O bot guarda o contador de usos de cada convite e, quando alguém entra, compara de novo: o convite cujo contador subiu é o usado. Convites de uso único (que somem ao ser usados) e o link personalizado do servidor também são reconhecidos. Se duas pessoas entrarem no mesmo instante por convites diferentes, o bot prefere dizer "não identificado" a creditar a pessoa errada.

| Campo | Descrição |
| :--- | :--- |
| `showInWelcome` | Adiciona "🎟️ Convidado por @Fulano · 3º recruta" ao embed de boas-vindas. O placeholder `{inviter}` também funciona no `welcomeMessage`. |
| `coinsPerInvite` | Moedas para quem convidou, por convidado válido (exige `economy.enabled`). |
| `rewardOnlyVerified` | A recompensa só sai quando o convidado **se verifica**. Evita farm com contas que entram e nunca participam. |
| `minAccountAgeDays` | Contas mais novas que isso entram no registro, mas não contam. |
| `removeTierOnLeave` | Se convidados saem, a contagem cai e o cargo de recrutador é recalculado. |
| `tiers` | Cargos por quantidade de convites válidos (o membro fica só com o maior). |

Regras anti-fraude: quem sai e volta mantém o convidador original e **nunca** rende recompensa duas vezes; ninguém conta como convidado de si mesmo. Tudo vai para o canal de logs, e quem convidou recebe uma DM quando ganha a recompensa.

> O bot precisa da permissão **Gerenciar Servidor** para ler os convites (já incluída em Administrador).

