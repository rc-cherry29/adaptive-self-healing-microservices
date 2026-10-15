import { useMemo } from 'react'
import { Activity, ArrowRight, CircleAlert, Database, GitBranch, Radio, ShieldCheck, Zap } from 'lucide-react'

const statusColors = {
  UP: '#34d399',
  REACHABLE: '#fbbf24',
  DOWN: '#fb7185',
  TIMEOUT: '#fb7185',
  UNKNOWN: '#94a3b8',
  ONLINE: '#34d399',
  OFFLINE: '#fb7185',
  CONFIGURED: '#60a5fa',
  AVAILABLE: '#34d399',
  NO_DATA: '#94a3b8',
  STATIC: '#94a3b8',
}

const names = ['Product Service', 'Order Service', 'Eureka Registry', 'API Gateway']

function statusOf(services, name) {
  return services.find((item) => item.name?.toLowerCase() === name.toLowerCase())?.status || 'UNKNOWN'
}

function DiagramNode({ x, y, width = 205, height = 94, title, subtitle, status, accent = '#67e8f9', pulse = false }) {
  const color = statusColors[status] || '#94a3b8'
  return <g className={pulse ? 'nrda-event-node' : ''}>
    <rect x={x} y={y} width={width} height={height} rx="16" fill="#14243c" stroke={pulse ? '#67e8f9' : '#35516f'} strokeWidth={pulse ? 2.8 : 1.5}/>
    <rect x={x + 13} y={y + 15} width="5" height={height - 30} rx="3" fill={accent} opacity=".9"/>
    <text x={x + 31} y={y + 31} fill="#eff6ff" fontSize="16" fontWeight="700">{title}</text>
    <text x={x + 31} y={y + 53} fill="#91a9c9" fontSize="11.5">{subtitle}</text>
    <circle cx={x + 35} cy={y + 74} r="5" fill={color} className={status === 'UP' || status === 'ONLINE' ? 'nrda-status-pulse' : ''}/>
    <text x={x + 48} y={y + 78} fill={color} fontSize="11" fontWeight="700">{status.replaceAll('_', ' ')}</text>
  </g>
}

function Connection({ d, color = '#4aa4d4', reverse = false, active = false }) {
  return <g>
    <path d={d} fill="none" stroke="#24425d" strokeWidth="6" strokeLinecap="round"/>
    <path d={d} fill="none" stroke={active ? '#67e8f9' : color} strokeWidth={active ? 3 : 2} strokeDasharray="7 12" strokeLinecap="round" className={reverse ? 'nrda-flow-reverse' : 'nrda-flow'}/>
  </g>
}

function MiniStatus({ name, status, probeMs }) {
  const color = statusColors[status] || '#94a3b8'
  return <div className="rounded-xl border border-[#2c405e] bg-[#101f35] px-4 py-3 flex items-center justify-between gap-3">
    <div className="min-w-0"><div className="text-sm font-semibold truncate">{name}</div><div className="text-xs text-[#839bb8] mt-1">{probeMs == null ? 'No probe yet' : `Probe ${probeMs} ms`}</div></div>
    <span className="text-xs font-bold shrink-0 flex items-center gap-1.5" style={{ color }}><span className="w-2 h-2 rounded-full" style={{ background: color }}/>{status}</span>
  </div>
}

export default function ArchitectureView({ services = [], experiments = [], summary, apiConnected = false, recentPulse = null, lastUpdated = null }) {
  const recent = experiments[0]
  const statusMap = useMemo(() => names.map((name) => ({ name, status: statusOf(services, name), probeMs: services.find((s) => s.name === name)?.probeMs })), [services])
  const highlightAction = Boolean(recentPulse && recentPulse.id === recent?.id)
  const highlightService = highlightAction && (recent?.serviceName || '').toUpperCase().includes('PRODUCT')
  const configuredMode = summary?.decisionMode || 'UNKNOWN'
  const databaseStatus = apiConnected && summary?.totalExperiments != null ? 'AVAILABLE' : 'NO_DATA'

  return <div className="space-y-6">
    <section className="panel p-5 lg:p-7">
      <div className="flex flex-wrap gap-4 items-start justify-between">
        <div>
          <div className="kicker">Architecture / live telemetry</div>
          <h2 className="text-xl font-bold mt-1">Microservices Recovery Topology</h2>
          <p className="text-sm muted mt-2 max-w-2xl">Health indicators are live HTTP probes. Animated lines illustrate logical connections, not measured network traffic.</p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <span className="rounded-full border border-teal-500/25 bg-teal-500/10 px-3 py-2 text-teal-200 flex items-center gap-2"><Radio size={14}/> Polls every 5 seconds</span>
          <span className="rounded-full border border-[#375274] bg-[#152a45] px-3 py-2 text-slate-300">{lastUpdated ? `Last refresh ${lastUpdated.toLocaleTimeString()}` : 'Waiting for API'}</span>
        </div>
      </div>
      <div className="mt-5 overflow-x-auto rounded-2xl border border-[#29425d] bg-[radial-gradient(circle_at_55%_40%,#172d4b,#0b1729_70%)]">
        <svg viewBox="0 0 1100 630" role="img" aria-label="NRDA architecture: Client to Gateway to Product and Order Services; Eureka registry; Monitoring Service; NRDA decision engine; Recovery Actions; MySQL" className="min-w-[850px] w-full h-auto">
          <defs><pattern id="nrda-grid" width="32" height="32" patternUnits="userSpaceOnUse"><path d="M 32 0 L 0 0 0 32" fill="none" stroke="#26415d" strokeWidth=".65" opacity=".55"/></pattern></defs>
          <rect width="1100" height="630" fill="url(#nrda-grid)"/>
          <text x="60" y="36" fill="#7f9cbb" fontSize="13" letterSpacing="2">REQUEST PATH</text>
          <text x="60" y="269" fill="#7f9cbb" fontSize="13" letterSpacing="2">OBSERVABILITY &amp; CONTROL</text>
          <Connection d="M 267 125 L 368 125"/>
          <Connection d="M 590 125 L 655 125 L 655 94 L 708 94"/>
          <Connection d="M 590 125 L 655 125 L 655 212 L 708 212"/>
          <Connection d="M 806 250 L 806 301 L 590 301 L 590 340" color="#718db8" reverse/>
          <Connection d="M 368 340 L 267 340" color="#718db8" reverse/>
          <Connection d="M 160 388 L 160 491 L 368 491" color="#a78bfa"/>
          <Connection d="M 590 491 L 650 491 L 650 368 L 708 368" color="#a78bfa" active={highlightAction}/>
          <Connection d="M 590 519 L 708 519" color="#34d399"/>
          <Connection d="M 806 323 L 806 250" color="#f59e0b" active={highlightService}/>
          <DiagramNode x={60} y={78} width={207} title="Client" subtitle="Frontend / API requests" status="STATIC" accent="#93c5fd"/>
          <DiagramNode x={368} y={78} width={222} title="API Gateway" subtitle="localhost:8080" status={statusOf(services, 'API Gateway')} accent="#22d3ee"/>
          <DiagramNode x={708} y={48} width={217} title="Product Service" subtitle="localhost:8081" status={statusOf(services, 'Product Service')} accent="#60a5fa" pulse={highlightService}/>
          <DiagramNode x={708} y={166} width={217} title="Order Service" subtitle="localhost:8082" status={statusOf(services, 'Order Service')} accent="#60a5fa"/>
          <DiagramNode x={368} y={293} width={222} title="Eureka Registry" subtitle="localhost:8761" status={statusOf(services, 'Eureka Registry')} accent="#a78bfa"/>
          <DiagramNode x={60} y={293} width={207} title="Monitoring API" subtitle="localhost:8083" status={apiConnected ? 'ONLINE' : 'OFFLINE'} accent="#34d399"/>
          <DiagramNode x={368} y={444} width={222} title="NRDA Decision" subtitle={`Configured: ${configuredMode}`} status="CONFIGURED" accent="#c084fc" pulse={highlightAction}/>
          <DiagramNode x={708} y={321} width={217} title="Recovery Actions" subtitle="Retry · Restart · Backup" status="CONFIGURED" accent="#fbbf24" pulse={highlightAction}/>
          <DiagramNode x={708} y={472} width={217} title="MySQL" subtitle="Experiment results" status={databaseStatus} accent="#34d399"/>
          <text x="60" y="606" fill="#7d98b5" fontSize="12">Animated paths = illustrative connections • Probe status = API data • Recovery highlights = new database records</text>
        </svg>
      </div>
      <div className="flex flex-wrap gap-5 mt-4 text-xs text-[#a8bad0]">
        <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-emerald-400"/> UP / ONLINE</span>
        <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-amber-400"/> REACHABLE (not health verified)</span>
        <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-rose-400"/> DOWN / TIMEOUT</span>
        <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-slate-400"/> UNKNOWN</span>
      </div>
    </section>
    <div className="grid grid-cols-1 xl:grid-cols-5 gap-5">
      <section className="panel p-5 xl:col-span-3">
        <div className="flex gap-2 items-center mb-4"><Activity size={18} className="text-cyan-300"/><h3 className="font-semibold">Current Service Health</h3></div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">{statusMap.map((item) => <MiniStatus key={item.name} {...item}/>)}</div>
        <p className="text-xs muted mt-4">UP requires an actuator health response. REACHABLE only confirms an HTTP response. Fault detection and experiment logging are separate from these probes.</p>
      </section>
      <section className={`panel p-5 xl:col-span-2 ${highlightAction ? 'nrda-event-panel' : ''}`}>
        <div className="flex gap-2 items-center mb-4"><Zap size={18} className="text-amber-300"/><h3 className="font-semibold">Latest Recovery Record</h3>{highlightAction && <span className="ml-auto text-xs text-cyan-300 font-semibold">NEW</span>}</div>
        {recent ? <div className="space-y-4">
          <div className="rounded-xl border border-[#365373] bg-[#152d4c] p-4"><div className="text-xs muted">Experiment #{recent.id} · {recent.decisionMode}</div><div className="text-xl font-bold mt-2">{String(recent.recoveryAction || 'UNKNOWN').replaceAll('_', ' ')}</div><div className="text-xs text-cyan-200 mt-1">{String(recent.failureType || '').replaceAll('_', ' ')}</div></div>
          <div className="flex justify-between gap-2 text-sm"><span className="muted">Verification</span><span className="text-right">{String(recent.verificationResult || 'UNKNOWN').replaceAll('_', ' ')}</span></div>
          <div className="flex justify-between gap-2 text-sm"><span className="muted">Action duration</span><span className="font-mono">{recent.recoveryTimeMs == null ? 'Not measured' : `${Number(recent.recoveryTimeMs).toLocaleString('en-IN')} ms`}</span></div>
          <p className="text-xs muted border-t border-[#2c3c58] pt-3">A recorded action is not proof that the current service is healthy. New rows are highlighted when detected by polling.</p>
        </div> : <div className="text-sm muted py-12 text-center">No recovery experiments recorded yet</div>}
      </section>
    </div>
    <div className="rounded-xl border border-[#33445d] bg-[#12233b] p-4 flex gap-3 text-sm text-[#aebfd8]"><CircleAlert size={19} className="shrink-0 text-amber-300"/><div><strong className="text-[#e2e9f6]">Demo note:</strong> This is a logical topology, not a packet-level network monitor. The Monitoring API reports four actual HTTP probes. The monitoring node is marked online when dashboard requests succeed; MySQL is marked available when the summary API returns database-backed counts.</div></div>
  </div>
}
