import { Typography } from 'antd'

import { InternalLinkCard } from './InternalLinkCard'

type InternalObjectLinkProps = {
  href: string
  compact?: boolean
}

export function InternalObjectLink({ href, compact = false }: InternalObjectLinkProps) {
  if (compact) {
    return (
      <Typography.Link href={href} onClick={(event) => event.preventDefault()}>
        {href}
      </Typography.Link>
    )
  }

  return <InternalLinkCard link={href} />
}
