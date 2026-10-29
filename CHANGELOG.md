# Changelog

## 4.4.0 — "Portões Vigiados" (2026-10-29)

- **Recuperação de entrada ao ligar**: o bot grava a cada minuto quando esteve online (`bot_presence`). Ao religar, quem está **sem nenhum cargo** recebe 🌱 Viajante e uma **DM se desculpando** com o botão dos portões; quem **entrou com o bot desligado** ganha também as boas-vindas no canal. Bots sem ⚙️ Autômato recebem o cargo. Membros com qualquer cargo (staff, antigos) ficam de fora. As DMs saem a cada 3 s, no máximo 100 por inicialização, e um resumo vai para o log de auditoria. A recuperação roda também ao fim de cada `/setup-server`, `/sync-server` e `/rebuild-server` (numa instalação nova os cargos só existem depois do sync), e conta como "entrou com o bot desligado" quem entrou nos últimos 14 dias sem o bot ter registrado a entrada. O log diz por que ela não rodou, quando não roda. Configurável em `onboarding.catchUp*`.
- **Limites nas calls**: ☕ Lareira 15 · 🎮 Arenas 5 · 👥 Duo 2 · 🎵 Bardo 10 · 🔒 Conselho 10; salas criadas em ➕ Criar Sala começam com limite 5 (o dono muda pelo painel). 🎪 Palco dos Eventos segue sem limite. Rode `/sync-server` para aplicar.

## 4.3.0 — "Carmesim e Ouro" (2026-10-27)

- **Identidade visual da logo**: paleta central em `Palette.java` — Carmesim `#4A0E0E`, Couro `#1E0707`, Ouro Thaluria `#D4AF6A`, Pergaminho `#EED49F` e Rubi `#8B1E1E`. Cor padrão dos embeds passa a ser o Ouro.
- **Cartões redesenhados** (boas-vindas, `/rank`, `/top`): fundo em degradê radial carmesim com vinheta, moldura dupla dourada, barras vazias em couro, anel do avatar e runas sempre dourados, nomes e níveis em pergaminho, medalhas bronze → prata envelhecida → ouro.
- **Fontes embutidas**: Cinzel (títulos e rótulos) e Inter (nomes e números) em `src/main/resources/fonts` (licença OFL) — o cartão fica igual no Windows e no Docker.
- **Painéis**: sem `thumbnailUrl`, levam a logo (`branding.iconUrl` ou ícone do servidor); o mesmo vale para o painel das salas temporárias e as boas-vindas. Botões passam a ser `SECONDARY` por padrão (o azul `PRIMARY` destoa da logo).
- **Moderação**: advertências, castigos, expulsões, mensagens apagadas e tickets fechados em Rubi; banimentos em Carmesim Imperial `#990F0F`.
- **Cargos com a temperatura da logo**: 👑 Soberano em preto `#010101` (o único nome preto do servidor), demais staff de Ouro `#D4AF6A` a Carmesim Imperial; níveis e recrutadores sobem de bronze `#8C583A` → prata `#A8A9AD` → ouro `#D4AF6A` → rubi `#C41E3A`.
- **Grafia "Thaluria"** em todos os textos visíveis (template embutido, rodapés, README e docs). O pacote Java segue `com.thalhuria.bot`.

## 4.2.0 — "Recrutamento" (2026-10-26)

- **Rastreio de convites**: "Fulano entrou pelo convite de Ciclano" nas boas-vindas e no log; recompensa em Thalers quando o convidado se verifica; cargos 📯 Recrutador (3), 🏹 Batedor do Reino (10) e 🦅 Mestre dos Recrutas (25); `/convites ver` e `/convites ranking`. Proteções contra contas novas, reentradas e auto-convite.
- **Logs em arquivo**: `logs/bot.log` (tudo) e `logs/erros.log` (avisos e erros com stack trace).
- **`/top` em imagem**: placar com medalhas, avatares e barras; 5 rankings (nível, Thalers, tempo em call, mensagens, convites); sua posição aparece no rodapé; quem saiu do servidor não entra no placar.
- **[docs/COMANDOS.md](docs/COMANDOS.md)**: guia de todos os comandos, botões e automações.
- **`iniciar-bot.bat`**: compila e roda o bot a partir de uma cópia do JAR, para que recompilar com o bot ligado não cause erros.

## 4.1.0 — "Brasões e Pergaminhos" (2026-10-26)

- **Cartão de boas-vindas redesenhado**: brilhos, partículas únicas por membro, avatar com glow, título espaçado, selo de habitante e marca no rodapé.
- **`/rank` em imagem**: posição, nível, barra de progresso, XP total, mensagens, tempo em call, Thalers, título atual e próxima recompensa. A cor do anel é a do cargo do membro.
- **Subida de nível em embed** com barra rumo ao próximo nível e próxima recompensa (sem notificar o membro a cada nível).
- **DM de boas-vindas** com guia e botão para os portões (`onboarding.dmWelcome`).
- **Leis do Reino reescritas**: 8 leis com tom próprio, escala de punições e canal de denúncia.
- Textos de entrada, verificação e de todas as respostas do bot (`messages.properties`) revisados.
- Validador avisa painéis perto do limite de 6.000 caracteres do Discord.
- **`/boas-vindas`** (só o dono): reenvia as boas-vindas marcando o membro.
- **Canais da Comunidade adotados**: o canal de regras e o de atualizações oficiais do Discord viram 📜・regras e 🛡️・guarda (sem duplicatas; a cópia antiga criada pelo bot é removida).

## 4.0.0 — "O Reino Completo" (2026-10-26)

### 👑 Cargos reformulados
Hierarquia completa e temática, de cima para baixo:
**👑 Soberano de Thalhuria** (dono — dado automaticamente ao dono do servidor) · **⚜️ Alto Conselho** (admins) · 🛡️ Guardião Real · 🧭 Guia do Reino · 🎪 Arauto dos Eventos · 🤝 Embaixador · 💎 Mecenas · ⚙️ Autômato · cores da loja (🌟 🌹 🌊 🌿 🌑) e 🎻 Bardo Errante · níveis (🐉 Lorde Dragão, 🏰 Barão, ⚔️ Cavaleiro, 🗡️ Escudeiro) · ✨ Cidadão · 🌱 Viajante · cargos de autoatribuição.

### ✨ Novidades (todo o roadmap)
- **Níveis e XP**: por mensagem (cooldown) e por minuto em call; cargos de recompensa; `/rank` e `/top`.
- **Economia**: Thalers 🪙 por atividade, `/daily` com sequência, `/saldo`, `/pagar` e `/loja` de cores e títulos (itens exclusivos por grupo).
- **Moderação**: `/mod advertir · castigo · remover-castigo · expulsar · banir · limpar · historico · perdoar`, histórico no banco, escalonamento automático (3/5/7 advertências).
- **AutoMod nativo**: palavras/golpes, convites, menções em massa e spam, criados pelo bot a cada sync.
- **Logs de moderação** em canal próprio: mensagens apagadas/editadas, banimentos com autor e motivo, castigos e calls.
- **Tickets por tipo** (Suporte, Denúncia, Parceria) com equipe própria e **transcrição HTML** nos logs e na DM.
- **Painéis com vários botões** (`TICKET`, `VERIFY`, `ROLE`, `URL`).
- **Cartão de boas-vindas em imagem**.
- **Eventos agendados** nativos com anúncio e lembrete (`/evento criar`, `/evento listar`).
- **Painel de controle das salas temporárias**: trancar, ocultar, renomear, limite e transferir.
- **Canais contadores** (membros, online, boosts).
- **`/sync-server simular:true`** e **`/backup`**.
- **Template por servidor** (`templates/<ID>.json`).
- **Textos configuráveis** (`messages.properties` + seção `messages`).
- **Docker**, **docker-compose** e **CI** no GitHub Actions.
- Novos canais no template: 📊 contadores, 🎉・eventos, 🏆・conquistas, 🛡️・logs-moderação, 🎪・Palco dos Eventos.

### 🔧 Melhorias
- SQLite em modo WAL com `busy_timeout` (escritas concorrentes de XP não falham).
- Mudanças de cargos cosméticos (menu de cargos, níveis, loja) não poluem mais o canal de logs.
- Status do bot atualizado a cada sync.

### 🔁 Migrando da 3.x
1. Pare o bot, atualize o código e o `.env` (veja as variáveis novas no `.env.example`).
2. `mvn clean package` e inicie.
3. **Servidor de testes / montagem:** `/setup-server` (recria do zero). **Servidor em uso:** `/sync-server simular:true` para revisar e depois `/sync-server`.
4. `/server-status` para conferir.

---

## 3.0.0 — "Reino da Resenha" (2026-10-26)

### ✨ Novidades

- **Template novo e temático** (`server-template.json`): 7 categorias, 24 canais, 12 cargos, 8 painéis. Identidade "Thalhuria" com paleta coesa e nomes próprios (Coroa, Guardiões, Cidadão, Viajante, Praça Central, Salões de Voz…).
- **Branding**: cor, rodapé e status do bot definidos no template.
- **Onboarding completo**: cargo automático na entrada, boas-vindas personalizadas, verificação por botão. Bots recebem o cargo de bot automaticamente.
- **Menu de cargos** (`SELF_ROLES`): interesses e notificações escolhidos pelo próprio membro.
- **Tickets**: botão abre canal privado com a staff; botão de fechar; tudo registrado nos logs.
- **Salas de voz temporárias** ("entre para criar"), limpas automaticamente — inclusive após reinício.
- **Sugestões**: reações de votação e tópico automático por mensagem.
- **Painéis vivos**: regras, FAQ, vantagens e guia da staff publicados pelo bot e **editados no lugar** a cada sync.
- **Placeholders** em todos os textos: `{channel:KEY}`, `{role:KEY}`, `{server}`, `{brand}`, `{user}`, `{count}`.
- **`/setup-server` agora começa do zero**: apaga todos os canais e cargos do servidor (inclusive os padrão do Discord e restos de versões antigas) e cria tudo pelo template. Em servidores com Comunidade, os canais obrigatórios de regras/atualizações são transferidos para `onboarding.rulesChannelKey` / `communityUpdatesChannelKey` e os antigos são apagados. Para aplicar mudanças sem apagar nada, use `/sync-server`.
- **`/rebuild-server`**: apaga e reconstrói o servidor do zero. Modo `bot` (só o que o bot criou) ou `tudo` (todos os canais e cargos, com confirmação digitando o nome do servidor). Valida o template antes de apagar qualquer coisa e entrega o resultado no novo canal de logs e por DM.
- **`/server-status`**: diagnóstico do servidor frente ao template (faltando, hierarquia, painéis, problemas).
- **Validador de template** + **JSON Schema** (autocomplete no VS Code) + testes automatizados.
- Novos campos de canal: `slowmode`, `nsfw`, `userLimit`.
- A ordem do template agora vale: hierarquia de cargos, ordem de categorias e de canais.

### 🐛 Bugs corrigidos

| Problema | Efeito antes | Agora |
| :--- | :--- | :--- |
| Itens localizados só pelo **nome** | Renomear um cargo/canal no template **duplicava** o item | Localiza pelo ID salvo; renomeia/move o existente |
| `ButtonInteractionListener` checava admin antes de olhar o botão | Qualquer botão novo seria bloqueado para membros comuns | Ignora botões que não são do reset |
| `tc.getTopic().equals(...)` | `NullPointerException` ao sincronizar canal sem tópico | Comparação segura com nulo |
| Template JSON inválido | Bot caía **silenciosamente** no template embutido e aplicava estrutura errada | Comando mostra o erro e não altera nada |
| Canal `ANNOUNCEMENT` em servidor sem Comunidade | Criação falhava | Cria como canal de texto e avisa no log |
| Comandos registrados na guilda de testes **e** globalmente | Cada comando aparecia duplicado | Com `TEST_GUILD_ID`, registra só na guilda |
| Busca do canal de logs por nome exato `logs-do-sistema` | Não achava o canal com nome estilizado | Usa o ID salvo; fallback por "contém" |
| Cada canal/cargo criado no setup gerava um log | Spam de dezenas de embeds no canal de logs | Logs individuais suprimidos durante setup/reset |
| Cargos reenviados ao Discord a cada sync | Requisições desnecessárias (rate limit) | Só envia o que mudou |
| Permissões de categoria não propagavam para canais existentes | Canais antigos ficavam com permissões desatualizadas | Canais são sincronizados com a categoria + regras próprias |
| Reset deixava canais órfãos | Salas privadas/tickets sobravam sem categoria | Reset apaga o conteúdo das categorias do bot |

### 🔁 Migrando um servidor que já usou a versão 2.x

As `key`s antigas foram mantidas, então basta:

1. Pare o bot, atualize o código e o `server-template.json`.
2. `mvn clean package` e inicie o bot.
3. Rode **`/sync-server`**. Os cargos, categorias e canais existentes serão **renomeados** para os novos nomes, e os novos itens serão criados.
4. Rode **`/server-status`** e confira se está tudo 🟢.
5. Arraste o cargo do bot para o **topo** da lista de cargos se o status apontar cargos acima do bot.
6. Dê o cargo **✨ │ Cidadão** aos membros antigos (ou peça que cliquem em ✅・verificação).

> Se preferir começar do zero num servidor de testes: `/reset-server` e depois `/setup-server`.
