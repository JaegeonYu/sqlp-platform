import { useQuery } from '@tanstack/react-query'

type SystemInfo = {
  name: string
  verdicts: string[]
}

async function fetchSystemInfo(): Promise<SystemInfo> {
  const res = await fetch('/api/system/info')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export default function App() {
  const { data, error, isPending } = useQuery({ queryKey: ['system-info'], queryFn: fetchSystemInfo })

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: 24 }}>
      <h1>SQLP Study</h1>
      {isPending && <p>API 연결 확인 중…</p>}
      {error && <p>API 연결 실패: {error.message}</p>}
      {data && (
        <p>
          API 연결됨 ({data.name}) · 채점 판정: {data.verdicts.join(', ')}
        </p>
      )}
    </main>
  )
}
