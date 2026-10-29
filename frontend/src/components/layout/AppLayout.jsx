import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function AppLayout() {
  return (
    <div className="bg-surface font-body-md text-on-surface antialiased min-h-screen">
      <Sidebar />
      <div className="pl-64">
        <Topbar />
        <main className="relative pt-16 bg-surface min-h-screen w-full px-gutter-lg py-space-xl">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
