export function getHomeRoute(role) {
  if (role === 'volunteer') {
    return '/my-duties'
  }
  return '/dashboard'
}
