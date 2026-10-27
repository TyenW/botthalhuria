# ✦ Bot Thaluria

Bot de Discord em **Java 17+ / JDA 5** que constrói um servidor inteiro a partir de **um único arquivo JSON** e depois cuida da comunidade: recebe quem chega com um cartão personalizado, verifica, distribui cargos, conta XP e moedas, modera com AutoMod nativo, abre tickets com transcrição, cria salas de voz sob demanda, agenda eventos e registra tudo.

```
/setup-server              →  apaga tudo e cria o servidor completo do zero
edite o JSON → /sync-server →  o Discord se ajusta (sem duplicar nada)
/sync-server simular:true  →  mostra o que mudaria, sem tocar em nada
```

| Cartão de boas-vindas | Cartão do `/rank` |
| :---: | :---: |
| ![Cartão de boas-vindas](docs/img/cartao-boas-vindas.png) | ![Cartão de rank](docs/img/cartao-rank.png) |

<p align="center"><img src="docs/img/placar-top.png" alt="Placar do /top" width="520"></p>

---

## 🌌 Recursos

### 🏗️ Infraestrutura como código
- **Template = fonte da verdade**: cargos, categorias, canais, permissões, painéis e todos os recursos abaixo vêm de [`server-template.json`](server-template.json).
- **Sincronização inteligente** por `key`: renomear, mover ou reordenar no JSON altera o item existente.
- **Simulação** (`/sync-server simular:true`) e **backup** (`/backup`) do servidor como template.
- **Template por servidor**: `templates/<ID_DO_SERVIDOR>.json` substitui o padrão naquele servidor.
- **Validação** em três camadas: JSON Schema no editor, testes no `mvn test`/CI e avisos nos comandos.

### 👑 Hierarquia de cargos
| Grupo | Cargos |
| :--- | :--- |
| Dono | **👑 Soberano de Thaluria** (dado automaticamente ao dono do servidor) |
| Administração | **⚜️ Alto Conselho** |
| Staff | 🛡️ Guardião Real (moderação) · 🧭 Guia do Reino (suporte) · 🎪 Arauto dos Eventos |
| Especiais | 🤝 Embaixador (parceiros) · 💎 Mecenas (apoiadores/boosters) · ⚙️ Autômato (bots) |
| Loja | 🌟 Ouro Real · 🌹 Rubi · 🌊 Safira · 🌿 Esmeralda · 🌑 Sombra · 🎻 Bardo Errante |
| Níveis | 🐉 Lorde Dragão (50) · 🏰 Barão (30) · ⚔️ Cavaleiro (15) · 🗡️ Escudeiro (5) |
| Base | ✨ Cidadão (verificado) · 🌱 Viajante (recém-chegado) |
| Autoatribuíveis | 🔔 Avisos · 🎉 Eventos · 🎮 Gamer · 🎨 Artista · 🎵 Melômano · 🎬 Cinéfilo |

### 🤝 Comunidade
| Recurso | Como funciona |
| :--- | :--- |
| 👋 **Onboarding** | Quem entra ganha 🌱 Viajante, um **cartão de boas-vindas em imagem** e uma **DM com guia** e botão para os portões. |
| ✅ **Verificação** | Botão troca Viajante por ✨ Cidadão e libera o servidor. |
| 🎭 **Menu de cargos** | Interesses e notificações escolhidos pelo próprio membro. |
| ⚔️ **Níveis e XP** | XP por mensagem (cooldown de 60s) e por minuto em call; cargos de recompensa; **cartão de rank em imagem** e `/top`. |
| 🪙 **Economia** | Thalers por atividade, `/daily` com sequência, `/pagar` e `/loja` de cores e títulos. |
| 🎫 **Tickets** | Botões por tipo (Suporte, Denúncia, Parceria), cada um com sua equipe; **transcrição HTML** ao fechar. |
| 🔊 **Salas temporárias** | Entre em `➕・Criar Sala` e ganhe uma call com painel: trancar, ocultar, renomear, limite, transferir. |
| 🎟️ **Convites** | "Fulano entrou pelo convite de Ciclano": aparece nas boas-vindas e no log; quem convida ganha Thalers quando o convidado se verifica e sobe para 📯 Recrutador (3) · 🏹 Batedor (10) · 🦅 Mestre dos Recrutas (25). |
| 📅 **Eventos** | `/evento criar` cria o evento nativo do Discord, anuncia marcando 🎉 Eventos e lembra 15 min antes. |
| 💡 **Sugestões** | Toda mensagem ganha ✅/❌ e um tópico de discussão. |
| 📊 **Contadores** | Canais no topo mostram membros, online e boosts ao vivo. |
| 📜 **Painéis vivos** | Regras, FAQ, vantagens, comandos e guia da staff — editados no lugar a cada sync. |

### 🛡️ Moderação
- `/mod advertir · castigo · remover-castigo · expulsar · banir · limpar · historico · perdoar`
- **Escalonamento automático**: 3 advertências = castigo de 1h · 5 = 24h · 7 = banimento (configurável).
- **AutoMod nativo do Discord** criado pelo bot: palavras/golpes bloqueados, convites, menções em massa e spam. Funciona mesmo com o bot offline.
- **Logs de moderação**: punições, mensagens apagadas/editadas, banimentos (com autor e motivo), castigos e movimentação em calls.
- **Auditoria do sistema**: entradas, saídas, cargos, canais, verificações, tickets e compras.

---

## 📑 Comandos

> 📖 Guia completo, com exemplos, botões e o que o bot faz sozinho: **[docs/COMANDOS.md](docs/COMANDOS.md)**


| Comando | Quem | Descrição |
| :--- | :--- | :--- |
| `/rank [usuario]` | Todos | Nível, barra de XP, posição, mensagens, tempo em call e carteira. |
| `/top [ranking]` | Todos | **Placar em imagem**: nível, Thalers, tempo em call, mensagens ou convites, com sua posição no rodapé. |
| `/daily` | Todos | Recompensa diária (bônus por sequência de dias). |
| `/saldo [usuario]` · `/pagar` · `/loja` | Todos | Economia e loja de cargos. |
| `/evento listar` | Todos | Próximos eventos. |
| `/convites ver [usuario]` · `/convites ranking` | Todos | Recrutas trazidos, pendentes, saídas e o ranking de recrutadores. |
| `/evento criar` | Gerenciar eventos | Cria, anuncia e agenda lembrete. |
| `/mod …` | Castigar membros | Ferramentas de moderação (cada ação confere a permissão específica). |
| `/boas-vindas usuario [dm]` | **Só o dono** | Reenvia as boas-vindas (cartão + mensagem) marcando o membro; `dm:true` reenvia também a DM. |
| `/server-status` | Admin | Diagnóstico do servidor frente ao template. |
| `/sync-server [simular]` | Admin | Aplica (ou simula) as mudanças do template sem apagar nada. |
| `/setup-server` | Admin | **Apaga todos os canais e cargos** e cria tudo do zero (confirmação por botão). |
| `/rebuild-server modo:` | Admin | Igual ao setup; modo `bot` apaga só o que o bot criou. |
| `/backup` | Admin | Exporta o servidor como template JSON. |
| `/membro configurar` | Admin | Dá o cargo de membro e cria sala privada. |
| `/reset-server` | Admin | Apaga tudo que o bot gerencia (com confirmação). |

---

## ⚙️ Instalação

### 1. Aplicação no Discord
1. [Discord Developer Portal](https://discord.com/developers/applications) → **New Application** → **Bot** → *Reset Token*.
2. Em **Privileged Gateway Intents**, ative **SERVER MEMBERS INTENT**.
   *(Opcional)* Ative **MESSAGE CONTENT INTENT** e coloque `MESSAGE_CONTENT_INTENT=true` no `.env` para os logs mostrarem o **conteúdo** de mensagens apagadas/editadas.
3. **OAuth2 → URL Generator**: escopos `bot` + `applications.commands`, permissão `Administrator`. Convide o bot.
4. Arraste o cargo do bot para o **topo** da lista de cargos.
5. *(Recomendado)* Ative a **Comunidade** do servidor (canal de anúncios de verdade e AutoMod completo).

### 2. `.env`
```bash
cp .env.example .env
```
| Variável | Padrão | Uso |
| :--- | :--- | :--- |
| `DISCORD_TOKEN` | — | Token do bot (obrigatório). |
| `TEST_GUILD_ID` | — | Comandos aparecem na hora (e só) nesse servidor. |
| `TEMPLATE_PATH` | `server-template.json` | Template padrão. |
| `TEMPLATES_DIR` | `templates` | Templates por servidor (`<ID>.json`). |
| `DATABASE_PATH` | `data/server_map.db` | Banco SQLite. |
| `BACKUPS_DIR` | `backups` | Saída do `/backup`. |
| `MESSAGE_CONTENT_INTENT` | `false` | Liga a intent privilegiada de conteúdo. |

### 3. Rodar
**Windows (recomendado):** dê dois cliques em `iniciar-bot.bat`. Ele compila, copia o JAR para `run/` e inicia a partir da cópia, então recompilar com o bot ligado não o quebra. Logs ficam em `logs/bot.log` e erros em `logs/erros.log`.

```bash
mvn clean test                          # inclui a validação do template
mvn package && java -jar target/botthalhuria-1.0.0.jar
```
**Com Docker (24/7 numa VPS):**
```bash
docker compose up -d --build            # banco, backups e templates ficam em volumes
docker compose logs -f
```

### 4. Primeira execução
1. `/setup-server` e confirme.
2. `/server-status` → tudo 🟢.
3. Dê ✨ Cidadão para a staff (ou clique em verificar). O dono já recebe 👑 automaticamente.

---

## 📂 Estrutura

```
botthalhuria/
├── server-template.json            # ⭐ o servidor inteiro
├── server-template.schema.json     # autocomplete/validação no editor
├── templates/                      # templates por servidor (<ID>.json)
├── Dockerfile · docker-compose.yml · .github/workflows/ci.yml
├── docs/  TEMPLATE.md · ROADMAP.md
└── src/main/
    ├── resources/messages.properties   # todos os textos do bot (sobrescrevíveis pelo template)
    └── java/com/thalhuria/bot/
        ├── config/      modelos do template + validação
        ├── database/    ServerMap · MemberStats (XP/moedas) · Infraction (moderação)
        ├── service/     provisionamento, painéis, onboarding, cartão, níveis, economia,
        │                moderação/AutoMod, tickets/transcrição, voz temporária, eventos,
        │                contadores, backup, logs
        ├── commands/    comandos slash
        └── listeners/   comunidade, painéis, salas de voz, logs de moderação, auditoria
```

### Banco (`data/server_map.db`, SQLite em modo WAL)
| Tabela | Conteúdo |
| :--- | :--- |
| `server_mappings` | key do template → ID do Discord (cargos, canais, painéis, salas temporárias) |
| `member_stats` | XP, nível, mensagens, minutos em call, moedas, sequência do daily |
| `infractions` | advertências, castigos, expulsões e banimentos |

---

## 📚 Documentação
- **[Comandos](docs/COMANDOS.md)** — todos os comandos, botões e automações.
- **[Guia do Template](docs/TEMPLATE.md)** — todos os campos, placeholders, permissões e receitas.
- **[Roadmap](docs/ROADMAP.md)** — o que foi feito e as próximas ideias.
- **[Changelog](CHANGELOG.md)** — histórico de versões e como migrar.
