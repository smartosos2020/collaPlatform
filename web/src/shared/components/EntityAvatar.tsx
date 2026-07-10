type EntityAvatarProps = {
  value?: string | null
  className?: string
}

export function EntityAvatar({ value, className }: EntityAvatarProps) {
  const classes = ['entity-avatar', className].filter(Boolean).join(' ')
  return <span className={classes}>{entityInitial(value)}</span>
}

function entityInitial(value?: string | null) {
  return (value?.trim()?.[0] ?? '?').toUpperCase()
}
