export function validNewPassword(value: string): boolean {
  const points = [...value]
  return (
    points.length >= 8 &&
    new TextEncoder().encode(value).length <= 72 &&
    !points.some((point) => {
      const code = point.codePointAt(0)!
      return code >= 0xd800 && code <= 0xdfff
    }) &&
    /\p{Lu}/u.test(value) &&
    /\p{Ll}/u.test(value) &&
    /[0-9]/.test(value) &&
    /[\p{P}\p{S}]/u.test(value)
  )
}
