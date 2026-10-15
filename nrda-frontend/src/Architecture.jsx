import { useMemo } from 'react'
import { Activity, ArrowRight, Clock3, Database, GitBranch, Info, Network, Server, ShieldCheck, Wifi, WifiOff, Zap } from 'lucide-react'

const PROBES = [
  { key: 'gateway', name: 'API Gateway', x: 275, y: 255, caption: ':8080' },
  { key: 'product', name: 'Product Service', x: 540, y: 140, caption: ':8081' },
  { key: 'order', name: 'Order Service', x: 540, y: 360, caption: ':8082' },
  { key: 'eureka', name: 'Eureka Registry', x: 810, y: 255, caption: ':8761' },
]
const EDGES = [
  ['M 177 255 L 210 255', 'Client request'],
  ['M 340 232 L 475 155', 'Route to Product'],
  ['M 340 278 L 475 344', 'Route to Order'],
  ['M 605 140 Q 740 140 810 220', 'Registration'],
  ['M 605 360 Q 740 360 810 290', 'Registration'],
  ['M 875 255 L 980 255', 'Discovery'],
  ['M 1045 295 L 1045 405', 'Health events'],
  ['M 980 445 L 875 445', 'Experiment logs'],
]
const KEY = {
  'API Gateway': 'gateway',
  'Product Service': 'product',
  'Order Service': 'order',
  'Eureka Registry': 'eureka',
}

function statusFor(name, services) {
  const item = services.find((s) => s.name === name)
  return item ? String(item.status || 'UNKNOWN').toUpperCase() : 'UNKNOWN'
}
function stateColor(status) {
  if (status === 'UP') return '#34d399'
  if (status === 'REACHABLE') return '#fbbf24'
  if (status === 'DOWN' || status === 'TIMEOUT') return '#fb7185'
  return '#94a3b8'
}
function Node({ x, y, title, caption, status, active = false, icon = 'server' }) {
  const color = stateColor(status)
  const iconText = icon === 'database' ? 'DB' : icon === 'brain' ? 'AI' : icon === 'client' ? 'UI' : icon === 'monitor' ? 'MON' : 'SVC'
  return <g className={active ? 'nrda-diagram-node nrda-diagram-active' : 'nrda-diagram-node'}>
    <rect x={x - 69} y={y - 39} width="138" height="78" rx="14" fill="#14243b" stroke={active ? '#22d3ee' : '#304664'} strokeWidth={active ? '2.5' : '1.3'}/>
    <rect x={x - 57} y={y - 25} width="34" height="34" rx="9" fill="#203a57"/>
    <text x={x - 40} y={y - 3} fill="#a8d8ff" fontSize="10" textAnchor="middle" fontWeight="800">{iconText}</text>
    <text x={x - 16} y={y - 9} fill="#eef5ff" fontSize="12" fontWeight="700">{title}</text>
    <text x={x - 16} y={y + 8} fill="#90a7c4" fontSize="10">{caption}</text>
    <circle cx={x - 53} cy={y + 25} r="4.5" fill={color} className={active ? 'nrda-dot-pulse' : ''}/>
    <text x={x - 43} y={y + 28} fill={color} fontSize="10" fontWeight="700">{status}</text>
  </g>
}

export default function Architecture({ services = [], experiments = [], summary, apiOnline = false, eventFlash = null, lastUpdated }) {
  const recent = experiments[0] || null
  const statusMap = useMemo(() => Object.fromEntries(Object.keys(KEY).map((name) => [KEY[name], statusFor(name, services)])), [services])
  const active = eventFlash && recent && Number(eventFlash.id) === Number(recent.id) ? eventFlash : null
  const productPulse = !!active && (recent?.serviceName || '').toUpperCase().includes('PRODUCT')
  const gatewayPulse = !!active && ['REROUTE', 'SWITCH_TO_BACKUP'].includes(recent?.recoveryAction)
  const failed = services.filter((s) => ['DOWN', 'TIMEOUT'].includes(String(s.status).toUpperCase()))
  const reachable = services.filter((s) => String(s.status).toUpperCase() === 'REACHABLE')

  return <div className="space-y-6">
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div className="panel p-5"><div className="flex justify-between items-center"><span className="kicker">Monitored endpoints</span><Network size={19} className="text-cyan-300"/></div><div className="mt-3 text-3xl font-bold">{services.length}<span className="text-sm font-normal text-slate-400"> / 4</span></div><div className="text-xs muted mt-2">Live backend probes every 5 seconds</div></div>
      <div className="panel p-5"><div className="flex justify-between items-center"><span className="kicker">Unreachable services</span><WifiOff size={19} className={failed.length ? 'text-rose-400' : 'text-emerald-400'}/></div><div className="mt-3 text-3xl font-bold">{failed.length}</div><div className="text-xs muted mt-2">DOWN or TIMEOUT from the latest probes</div></div>
      <div className="panel p-5"><div className="flex justify-between items-center"><span className="kicker">Decision mode</span><GitBranch size={19} className="text-violet-300"/></div><div className="mt-3 text-3xl font-bold">{summary?.decisionMode || '—'}</div><div className="text-xs muted mt-2">Configured in Spring Boot</div></div>
    </div>

    <section className="panel p-5 md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
        <div><p className="kicker mb-1">Live topology</p><h2 className="text-xl font-semibold">Microservices Architecture</h2><p className="text-sm muted mt-1">Animated logical connections · service health from Monitoring API</p></div>
        <div className="flex flex-wrap gap-3 text-xs items-center">
          <span className="flex items-center gap-1.5 text-emerald-300"><span className="w-2 h-2 rounded-full bg-emerald-400"/>UP</span>
          <span className="flex items-center gap-1.5 text-amber-300"><span className="w-2 h-2 rounded-full bg-amber-400"/>Reachable</span>
          <span className="flex items-center gap-1.5 text-rose-300"><span className="w-2 h-2 rounded-full bg-rose-400"/>Down / timeout</span>
        </div>
      </div>
      <div className="nrda-diagram-scroll rounded-2xl border border-[#2a4161] bg-[#0b182a]">
        <svg viewBox="0 0 1170 545" role="img" aria-label="Logical topology showing client, API gateway, product and order services, Eureka, Monitoring Service, NRDA engine and MySQL. Colors reflect monitored status when available." className="nrda-topology-svg">
          <defs>
            <pattern id="nrda-grid" width="26" height="26" patternUnits="userSpaceOnUse"><path d="M 26 0 L 0 0 0 26" fill="none" stroke="#24344b" strokeWidth="0.65"/></pattern>
            <marker id="nrda-arrow" markerWidth="7" markerHeight="7" refX="5" refY="3" orient="auto"><path d="M0 0 L6 3 L0 6" fill="none" stroke="#436687" strokeWidth="1.2"/></marker>
          </defs>
          <rect x="0" y="0" width="1170" height="545" fill="url(#nrda-grid)"/>
          <text x="85" y="70" fill="#7893b4" fontSize="12" fontWeight="700" letterSpacing="2">CLIENT LAYER</text>
          <text x="270" y="70" fill="#7893b4" fontSize="12" fontWeight="700" letterSpacing="2">GATEWAY</text>
          <text x="535" y="70" fill="#7893b4" fontSize="12" fontWeight="700" letterSpacing="2">SERVICES</text>
          <text x="790" y="70" fill="#7893b4" fontSize="12" fontWeight="700" letterSpacing="2">DISCOVERY / STORAGE</text>
          <text x="992" y="70" fill="#7893b4" fontSize="12" fontWeight="700" letterSpacing="2">RECOVERY</text>
          {EDGES.map(([d, label], i) => <g key={label}>
            <path d={d} fill="none" stroke="#385776" strokeWidth="2" strokeLinecap="round" markerEnd="url(#nrda-arrow)"/>
            <path d={d} fill="none" stroke="#35b7d9" strokeWidth="2.2" strokeDasharray="4 15" strokeLinecap="round" className="nrda-flow-line" style={{ animationDelay: `${i * -0.7}s` }}/>
          </g>)}
          <Node x={110} y={255} title="Client" caption="React :5174" status="UI ACTIVE" icon="client"/>
          {PROBES.map((p) => <Node key={p.key} x={p.x} y={p.y} title={p.name} caption={p.caption} status={statusMap[p.key]} active={p.key === 'gateway' ? gatewayPulse : p.key === 'product' ? productPulse : false}/>)}
          <Node x={1045} y={255} title="Monitoring" caption="API :8083" status={apiOnline ? 'API ONLINE' : 'UNKNOWN'} icon="monitor"/>
          <Node x={1045} y={445} title="NRDA Engine" caption="Decision logic" status={summary?.decisionMode || 'UNKNOWN'} active={!!active} icon="brain"/>
          <Node x={810} y={445} title="MySQL" caption="experiment_results" status={apiOnline && summary ? 'QUERY OK' : 'UNKNOWN'} icon="database"/>
          <text x="590" y="514" fill="#738ba9" fontSize="12" textAnchor="middle">Flow animation illustrates logical connections, not measured request traffic.</text>
        </svg>
      </div>
      <div className="flex flex-wrap items-start justify-between gap-3 mt-4 text-xs muted"><span><Info size={13} className="inline mr-1"/>REACHABLE confirms HTTP connectivity, not an Actuator UP result.</span><span>{lastUpdated ? `Last polled ${lastUpdated.toLocaleTimeString()}` : 'Waiting for first poll'}</span></div>
      {reachable.length > 0 && <p className="text-xs text-amber-200/80 mt-2">{reachable.map((s) => s.name).join(', ')} {reachable.length === 1 ? 'responds' : 'respond'} over HTTP, but full health is not verified.</p>}
    </section>

    <div className="grid grid-cols-1 xl:grid-cols-5 gap-5">
      <section className="panel p-5 xl:col-span-3"><div className="flex justify-between items-center gap-3"><div><p className="kicker mb-1">Recovery stream</p><h2 className="text-lg font-semibold">Latest NRDA Event</h2></div><Zap size={19} className="text-cyan-300"/></div>
        {recent ? <div className="mt-5 rounded-xl bg-[#152943] border border-[#2b486b] p-4 flex flex-wrap justify-between gap-5">
          <div><p className="text-xs muted">Last recorded action · Experiment #{recent.id}</p><p className="text-xl font-bold mt-1">{String(recent.recoveryAction || 'UNKNOWN').replaceAll('_', ' ')}</p><p className="text-sm text-[#a6bbd9] mt-1">{String(recent.failureType || 'UNKNOWN').replaceAll('_', ' ')} · {recent.decisionMode}</p></div>
          <div className="text-right"><span className={`inline-block rounded-full border px-3 py-1 text-xs font-semibold ${recent.verificationResult === 'RECOVERY_FAILED' ? 'border-rose-400/30 text-rose-300 bg-rose-400/10' : recent.verificationResult === 'RECOVERY_SUCCESS' ? 'border-emerald-400/30 text-emerald-300 bg-emerald-400/10' : 'border-amber-400/30 text-amber-200 bg-amber-400/10'}`}>{String(recent.verificationResult || 'UNVERIFIED').replaceAll('_', ' ')}</span><p className="text-xs muted mt-3">Action execution: {recent.recoveryTimeMs == null ? '—' : `${Number(recent.recoveryTimeMs).toLocaleString('en-IN')} ms`}</p></div>
        </div> : <p className="text-sm muted mt-5">No recovery events recorded yet.</p>}
        <div className="mt-4 text-xs muted">{active ? <span className="text-cyan-300 font-semibold"><Activity size={14} className="inline mr-1"/>New experiment detected in this session — diagram highlighted.</span> : 'Historical events are not treated as active recovery. New records briefly highlight affected nodes.'}</div>
      </section>
      <section className="panel p-5 xl:col-span-2"><p className="kicker mb-1">System state</p><h2 className="text-lg font-semibold">Monitoring Summary</h2>
        <div className="mt-5 space-y-4 text-sm">
          <div className="flex items-center justify-between gap-3"><span className="muted flex gap-2 items-center"><Wifi size={15}/> Monitoring API</span><span className={apiOnline ? 'text-emerald-300' : 'text-amber-300'}>{apiOnline ? 'Responding' : 'Not confirmed'}</span></div>
          <div className="flex items-center justify-between gap-3"><span className="muted flex gap-2 items-center"><Server size={15}/> Unreachable services</span><span className={failed.length ? 'text-rose-300' : 'text-emerald-300'}>{failed.length}</span></div>
          <div className="flex items-center justify-between gap-3"><span className="muted flex gap-2 items-center"><Database size={15}/> Stored experiments</span><span>{summary?.totalExperiments ?? '—'}</span></div>
          <div className="flex items-center justify-between gap-3"><span className="muted flex gap-2 items-center"><ShieldCheck size={15}/> Recovery mode</span><span className="text-cyan-300">{summary?.decisionMode || '—'}</span></div>
        </div>
      </section>
    </div>
  </div>
}
