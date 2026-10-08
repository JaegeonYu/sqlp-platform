import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'

/**
 * 사용자가 쓴 마크다운 렌더링. raw HTML은 처리하지 않고(react-markdown 기본값),
 * rehype-sanitize로 위험한 속성·URL(javascript: 등)을 한 번 더 걸러 XSS를 막는다.
 */
export function Markdown({ source }: { source: string | null | undefined }) {
  if (!source) return null
  return (
    <div className="markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {source}
      </ReactMarkdown>
    </div>
  )
}
