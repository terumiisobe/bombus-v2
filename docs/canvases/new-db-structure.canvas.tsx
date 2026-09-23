import {
  Callout,
  Card,
  CardBody,
  CardHeader,
  Code,
  Divider,
  Grid,
  H1,
  H2,
  H3,
  Pill,
  Row,
  Stack,
  Stat,
  Table,
  Text,
  computeDAGLayout,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

type Role = "identity" | "hive" | "reference" | "conversation";

type Column = {
  name: string;
  type: string;
  constraint: string;
};

type TableDef = {
  id: string;
  role: Role;
  purpose: string;
  fromLegacy: string;
  columns: Column[];
  extras: string[];
};

const TABLES: TableDef[] = [
  {
    id: "usuario",
    role: "identity",
    purpose: "Customer account. The bot never logs in as this user; it only resolves a WhatsApp number to this id.",
    fromLegacy: "users. password renamed to password_hash. Ids preserved.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "email", type: "VARCHAR(255)", constraint: "NOT NULL UNIQUE" },
      { name: "password_hash", type: "VARCHAR(255)", constraint: "NOT NULL" },
      { name: "created_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
      { name: "updated_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
    ],
    extras: [],
  },
  {
    id: "usuario_whatsapp",
    role: "conversation",
    purpose: "Links one E.164 phone number to a usuario. The webhook matches Twilio From after stripping the whatsapp: prefix.",
    fromLegacy: "New table. Empty until a production number is inserted.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "usuario_id", type: "BIGINT", constraint: "NOT NULL FK usuario" },
      { name: "phone_number", type: "VARCHAR(20)", constraint: "NOT NULL UNIQUE" },
      { name: "display_name", type: "VARCHAR(100)", constraint: "nullable" },
      { name: "linked_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
      { name: "active", type: "BOOLEAN", constraint: "NOT NULL DEFAULT true" },
    ],
    extras: ["Only active = true rows resolve as a known customer."],
  },
  {
    id: "sessao_chat",
    role: "conversation",
    purpose: "One sliding conversation window per WhatsApp user. Context is a bounded JSON array of recent turns.",
    fromLegacy: "New table. V2 adds the unique constraint the upsert depends on.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "usuario_whatsapp_id", type: "BIGINT", constraint: "NOT NULL UNIQUE FK" },
      { name: "conversation_context", type: "JSONB", constraint: "NOT NULL" },
      { name: "last_message_at", type: "TIMESTAMPTZ", constraint: "NOT NULL" },
      { name: "created_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
      { name: "expires_at", type: "TIMESTAMPTZ", constraint: "NOT NULL" },
    ],
    extras: [
      "uq_sessao_chat_usuario_whatsapp on usuario_whatsapp_id",
      "idx_sessao_chat_expira on expires_at",
      "ON CONFLICT (usuario_whatsapp_id) is the write path.",
    ],
  },
  {
    id: "especie",
    role: "reference",
    purpose: "Species vocabulary the model maps a Portuguese phrase onto. Seeded by V2 with explicit ids 1–10.",
    fromLegacy: "species. Copied by abbreviation, not scientific name.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "abbreviation", type: "VARCHAR(50)", constraint: "NOT NULL UNIQUE" },
      { name: "common_name", type: "VARCHAR(50)", constraint: "NOT NULL" },
      { name: "scientific_name", type: "VARCHAR(50)", constraint: "NOT NULL UNIQUE" },
    ],
    extras: ["common_name is not unique: Manduri is both MD and MT."],
  },
  {
    id: "status_colmeia",
    role: "reference",
    purpose: "Canonical hive-status vocabulary. The default count excludes the row named perdida.",
    fromLegacy: "Replaces the free-text colmeias.status column.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "name", type: "VARCHAR(50)", constraint: "NOT NULL UNIQUE" },
    ],
    extras: ["Seeded names: desenvolvendo, recuperando, estavel, perdida, desconhecido."],
  },
  {
    id: "meliponario",
    role: "hive",
    purpose: "An apiary owned by a usuario. A customer can own several; counts sum across all of them.",
    fromLegacy: "meliponary. Ids and owner_id preserved.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "name", type: "VARCHAR(50)", constraint: "NOT NULL" },
      { name: "address", type: "VARCHAR(255)", constraint: "NOT NULL" },
      { name: "owner_id", type: "BIGINT", constraint: "NOT NULL FK usuario" },
    ],
    extras: [],
  },
  {
    id: "colmeia",
    role: "hive",
    purpose: "One hive. Status is not a column here; the current status is the latest history row.",
    fromLegacy: "colmeias. meliponary_id → meliponario_id, starting_date → start_date, status dropped.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "code", type: "INT", constraint: "nullable" },
      { name: "species_id", type: "BIGINT", constraint: "NOT NULL FK especie" },
      { name: "meliponario_id", type: "BIGINT", constraint: "NOT NULL FK meliponario" },
      { name: "start_date", type: "TIMESTAMPTZ", constraint: "nullable" },
    ],
    extras: ["Identity is new. Legacy colmeias.id had no sequence."],
  },
  {
    id: "colmeia_status_historico",
    role: "hive",
    purpose: "Status history. The count query treats the latest row per hive as the current status.",
    fromLegacy: "New. One row is seeded per existing hive from its old text status.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "colmeia_id", type: "BIGINT", constraint: "NOT NULL FK, ON DELETE CASCADE" },
      { name: "status_id", type: "BIGINT", constraint: "NOT NULL FK status_colmeia" },
      { name: "recorded_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
    ],
    extras: ["idx_colmeia_status_hist_atual (colmeia_id, recorded_at DESC). Latest = recorded_at DESC, id DESC."],
  },
  {
    id: "localizacao",
    role: "hive",
    purpose: "GPS readings for a hive. The bot does not read this table.",
    fromLegacy: "location, plus a new recorded_at column.",
    columns: [
      { name: "id", type: "BIGINT", constraint: "identity PK" },
      { name: "colmeia_id", type: "BIGINT", constraint: "NOT NULL FK, ON DELETE CASCADE" },
      { name: "latitude", type: "DECIMAL(9,6)", constraint: "NOT NULL" },
      { name: "longitude", type: "DECIMAL(9,6)", constraint: "NOT NULL" },
      { name: "accuracy_meters", type: "DECIMAL(5,2)", constraint: "nullable" },
      { name: "recorded_at", type: "TIMESTAMPTZ", constraint: "NOT NULL DEFAULT now()" },
    ],
    extras: ["idx_localizacao_atual (colmeia_id, recorded_at DESC)."],
  },
];

const EDGES = [
  { from: "usuario", to: "meliponario" },
  { from: "usuario", to: "usuario_whatsapp" },
  { from: "usuario_whatsapp", to: "sessao_chat" },
  { from: "especie", to: "colmeia" },
  { from: "meliponario", to: "colmeia" },
  { from: "colmeia", to: "colmeia_status_historico" },
  { from: "status_colmeia", to: "colmeia_status_historico" },
  { from: "colmeia", to: "localizacao" },
];

const SPECIES = [
  ["1", "JT", "Jataí", "Tetragosnisca angustula"],
  ["2", "EM", "Mirim emerina", "Plebeia emerina"],
  ["3", "PG", "Mirim", "Plebeia gigantea"],
  ["4", "MQ", "Mandaçaia", "Melipona quadrifasciata"],
  ["5", "MB", "Guaraipo", "Melipona bicolor"],
  ["6", "MD", "Manduri", "Melipona marginata"],
  ["7", "MT", "Manduri", "Melipona torrida"],
  ["8", "SB", "Tubuna", "Scaptotrigona bipunctata"],
  ["9", "SD", "Canudo", "Scaptotrigona depilis"],
  ["10", "MM", "Mirim mosquito", "Plebeia droryana"],
];

const STATUSES = [
  ["1", "desenvolvendo", "Included in a plain count"],
  ["2", "recuperando", "Included in a plain count"],
  ["3", "estavel", "Included in a plain count"],
  ["4", "perdida", "Excluded unless the user asks for it"],
  ["5", "desconhecido", "Included in a plain count"],
];

const ROLE_ORDER: Role[] = ["identity", "hive", "reference", "conversation"];

const ROLE_LABEL: Record<Role, string> = {
  identity: "Identity",
  hive: "Hives",
  reference: "Reference",
  conversation: "Conversation",
};

function SchemaGraph({
  selected,
  onSelect,
}: {
  selected: string;
  onSelect: (id: string) => void;
}) {
  const theme = useHostTheme();
  const layout = computeDAGLayout({
    nodes: TABLES.map((table) => ({ id: table.id })),
    edges: EDGES,
    direction: "horizontal",
    nodeWidth: 188,
    nodeHeight: 36,
    rankGap: 52,
    nodeGap: 14,
    padding: 8,
  });
  const byId = new Map(layout.nodes.map((node) => [node.id, node]));

  return (
    <svg
      width="100%"
      viewBox={`0 0 ${layout.width} ${layout.height}`}
      role="img"
      aria-label="Foreign-key graph of the nine public tables. Arrow points from the referenced table to the table that holds the foreign key."
    >
      {layout.edges.map((edge) => {
        const midX = (edge.sourceX + edge.targetX) / 2;
        return (
          <path
            key={`${edge.from}-${edge.to}`}
            d={`M ${edge.sourceX} ${edge.sourceY} C ${midX} ${edge.sourceY}, ${midX} ${edge.targetY}, ${edge.targetX} ${edge.targetY}`}
            fill="none"
            stroke={theme.stroke.secondary}
            strokeWidth={1.25}
          />
        );
      })}
      {layout.nodes.map((node) => {
        const active = node.id === selected;
        return (
          <g key={node.id} onClick={() => onSelect(node.id)} style={{ cursor: "pointer" }}>
            <rect
              x={node.x}
              y={node.y}
              width={188}
              height={36}
              rx={6}
              fill={active ? theme.accent.primary : theme.bg.elevated}
              stroke={active ? theme.accent.primary : theme.stroke.primary}
            />
            <text
              x={node.x + 94}
              y={node.y + 23}
              textAnchor="middle"
              fill={active ? theme.text.onAccent : theme.text.primary}
              fontSize={12}
              fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace"
            >
              {node.id}
            </text>
          </g>
        );
      })}
      {layout.edges.map((edge) => {
        const target = byId.get(edge.to);
        if (!target) return null;
        return (
          <polygon
            key={`arrow-${edge.from}-${edge.to}`}
            points={`${target.x},${target.y + 18} ${target.x - 7},${target.y + 14} ${target.x - 7},${target.y + 22}`}
            fill={theme.stroke.secondary}
          />
        );
      })}
    </svg>
  );
}

export default function NewDbStructure() {
  const [selected, setSelected] = useCanvasState("selected-table", "colmeia");
  const table = TABLES.find((item) => item.id === selected) ?? TABLES[0];

  return (
    <Stack gap={24}>
      <Stack gap={6}>
        <H1>New public schema</H1>
        <Text tone="secondary">
          Target of the rebuild. Flyway applies V1 (tables), V2 (species, first four statuses, chat-session unique constraint), and V3 (status desconhecido). Source: V1__baseline.sql, V2__reference_data.sql, V3__status_desconhecido.sql.
        </Text>
      </Stack>

      <Grid columns={4} gap={16}>
        <Stat value="9" label="Tables in public" />
        <Stat value="8" label="Foreign keys" />
        <Stat value="10" label="Seeded species" />
        <Stat value="5" label="Canonical statuses" tone="info" />
      </Grid>

      <Stack gap={8}>
        <H2>Relationships</H2>
        <Text tone="secondary" size="small">
          Arrow points from the referenced table to the table that stores the foreign key. Select a table to inspect its columns.
        </Text>
        <SchemaGraph selected={table.id} onSelect={setSelected} />
      </Stack>

      <Callout tone="info" title="How a count is computed">
        usuario.id → meliponario.owner_id → colmeia, joined to especie, with the current status taken from the latest colmeia_status_historico row. A plain count drops the status named perdida. The model never supplies the number.
      </Callout>

      <Stack gap={10}>
        <H2>Tables</H2>
        {ROLE_ORDER.map((role) => (
          <div key={role}>
            <Row gap={8} align="center" wrap>
              <Text size="small" tone="tertiary" style={{ width: 108 }}>
                {ROLE_LABEL[role]}
              </Text>
              {TABLES.filter((item) => item.role === role).map((item) => (
                <span key={item.id}>
                  <Pill active={item.id === table.id} onClick={() => setSelected(item.id)}>
                    {item.id}
                  </Pill>
                </span>
              ))}
            </Row>
          </div>
        ))}
      </Stack>

      <Card>
        <CardHeader trailing={<Pill size="sm" active>{ROLE_LABEL[table.role]}</Pill>}>
          {table.id}
        </CardHeader>
        <CardBody>
          <Stack gap={12}>
            <Text>{table.purpose}</Text>
            <Text size="small" tone="secondary">
              From legacy: {table.fromLegacy}
            </Text>
            <Table
              headers={["Column", "Type", "Constraint"]}
              columnAlign={["left", "left", "left"]}
              rows={table.columns.map((column) => [column.name, column.type, column.constraint])}
              striped
            />
            {table.extras.length > 0 ? (
              <Stack gap={4}>
                {table.extras.map((extra) => (
                  <div key={extra}>
                    <Text size="small" tone="secondary">
                      {extra}
                    </Text>
                  </div>
                ))}
              </Stack>
            ) : null}
          </Stack>
        </CardBody>
      </Card>

      <Divider />

      <Grid columns={2} gap={24}>
        <Stack gap={8}>
          <H3>Seeded statuses</H3>
          <Text size="small" tone="tertiary">
            V2 ids 1–4 plus V3 id 5. Legacy text values map onto these names during the copy. See docs/colmeia-status-vocabulary.md.
          </Text>
          <Table
            headers={["Id", "Name", "Plain count"]}
            rows={STATUSES}
            striped
          />
        </Stack>
        <Stack gap={8}>
          <H3>Seeded species</H3>
          <Text size="small" tone="tertiary">
            Match production rows on abbreviation. scientific_name for JT is stored as Tetragosnisca angustula.
          </Text>
          <Table
            headers={["Id", "Abbr", "Common name", "Scientific name"]}
            rows={SPECIES}
            striped
            stickyHeader
          />
        </Stack>
      </Grid>

      <Stack gap={8}>
        <H2>Not in the new schema</H2>
        <Text>
          <Code>user_session</Code>, <Code>multiplication_operation</Code>, and <Code>multiplication_resource</Code> stay in the renamed <Code>legacy</Code> schema. The app has no tables or queries for them.
        </Text>
      </Stack>
    </Stack>
  );
}
