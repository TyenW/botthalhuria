# 🗺️ Roadmap

## ✅ Concluído na 4.0 (todo o roadmap anterior)

| # | Item | Onde está |
| :---: | :--- | :--- |
| 1 | **Níveis e XP** por mensagem e por call, recompensas, `/rank`, `/top` | `ActivityService`, `LevelCommands`, seção `leveling` |
| 2 | **Moderação**: `/mod` completo, histórico, perdão, escalonamento automático, **AutoMod nativo** | `ModerationService`, `ModCommand`, seção `moderation` |
| 3 | **Transcrição de tickets** em HTML (logs + DM) | `TranscriptService`, `TicketService` |
| 4 | **Logs de moderação**: mensagens apagadas/editadas, bans com autor, castigos, calls; canal próprio | `ModLogListener`, `ModLogService` |
| 5 | **Cartão de boas-vindas** em imagem | `WelcomeCardRenderer`, seção `welcomeCard` |
| 6 | **Painéis com vários botões** e **tipos de ticket** | `PanelService`, `tickets.types`, `panel.buttons` |
| 7 | **Eventos agendados** nativos com anúncio e lembrete | `EventService`, `EventCommand`, seção `events` |
| 8 | **Painel de controle das salas temporárias** | `TempVoiceService`, `TempVoiceControlListener` |
| 9 | **Economia e loja de cargos** | `EconomyService`, `EconomyCommands`, seção `economy` |
| 10 | **Canais contadores** | `StatsCounterService`, campo `counter` |
| 11 | **Simulação do sync** (`/sync-server simular:true`) | `ServerProvisioningService.previewSync` |
| 12 | **Backup/exportação** como template | `BackupService`, `/backup` |
| 13 | Status do bot atualizado no sync | `SyncServerCommand`, `RebuildServerCommand` |
| 14 | **Template por servidor** | `TemplateService` + `templates/<ID>.json` |
| 15 | **Docker**, **CI**, SQLite em WAL, logs sem ruído de cargos cosméticos | `Dockerfile`, `.github/workflows/ci.yml`, `DatabaseManager`, `GuildAuditListener` |
| 16 | **Textos configuráveis** | `messages.properties` + seção `messages` |

Também na 4.0: hierarquia de cargos completa com **cargo do dono** (automático) e **cargo de admins**, e `/setup-server` recriando tudo do zero.

---

## 🔜 Próximas ideias

Legenda de esforço: 🟢 poucas horas · 🟡 1–2 dias · 🔴 vários dias

### Prioridade 1 — identidade única
| Ideia | Esforço | Notas |
| :--- | :---: | :--- |
| **Casas / Facções** (🐉 Dragões, 🦉 Corujas, 🐺 Lobos): escolha na verificação, XP soma para a casa, ranking semanal em anúncios | 🟡 | Reaproveita XP e menu de cargos; nova seção `houses` |
| **Títulos por tempo de casa** (Cavaleiro aos 30 dias, Barão aos 90...) | 🟢 | Tarefa diária no agendador comparando `member.getTimeJoined()` |
| **Missões diárias** ("10 mensagens", "30 min em call") com recompensa em XP/moedas | 🟡 | Nova tabela `quests`; usa os mesmos eventos do `ActivityService` |
| **Cartão de rank em imagem** no `/rank` | 🟢 | Reaproveita o `WelcomeCardRenderer` |

### Prioridade 2 — engajamento
| Ideia | Esforço | Notas |
| :--- | :---: | :--- |
| **Procura de grupo**: `/procurar jogo vagas` → card com botão Entrar → cria sala temporária ao lotar | 🟡 | Integra com `TempVoiceService` |
| **Sorteios** com botão de participar e requisito de nível/cargo | 🟡 | Persistir no banco para sobreviver a reinícios |
| **Aniversários** com cargo 🎂 de 24h | 🟢 | Tabela `birthdays` + tarefa diária |
| **Mural da Fama** (starboard: 5+ ⭐ copia a mensagem) | 🟢 | Exige `MESSAGE_CONTENT_INTENT` para copiar o texto |
| **Avaliação do atendimento** (1–5 ⭐ ao fechar ticket) e média no `/server-status` | 🟢 | Botões na DM da transcrição |

### Prioridade 3 — staff e operação
| Ideia | Esforço | Notas |
| :--- | :---: | :--- |
| **Antirraid**: muitas entradas em poucos segundos ativam modo lento e alertam a staff | 🟢 | Janela deslizante no `CommunityListener` |
| **`/lockdown`** e **`/unlock`** dos canais da comunidade | 🟢 | Override temporário em `ROLE_MEMBER` |
| **Notas internas** sobre membros (`/mod nota`) | 🟢 | Novo `type = NOTE` na tabela `infractions` |
| **`/embed`** com formulário para a staff criar anúncios | 🟡 | Modal + pré-visualização |
| **Mensagens agendadas/recorrentes** | 🟡 | Tabela + agendador |
| **Restaurar backup por comando** (`/backup restaurar`) | 🟢 | Hoje é copiar o arquivo para `templates/` |
| **Painel web** para editar o template e ver estatísticas | 🔴 | Javalin/Spring + OAuth2 do Discord |
| Mover os últimos textos fixos (respostas do menu de cargos, títulos de logs) para `messages.properties` | 🟢 | |
