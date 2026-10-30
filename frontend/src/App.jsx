import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ProtectedRoute from './components/layout/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import EventsPage from './pages/EventsPage'
import TasksPage from './pages/TasksPage'
import VolunteersPage from './pages/VolunteersPage'
import DashboardPage from './pages/DashboardPage'
import AssignmentPlanPage from './pages/AssignmentPlanPage'
import AiAssistantPage from './pages/AiAssistantPage'
import VolunteerProfilePage from './pages/VolunteerProfilePage'
import SettingsPage from './pages/SettingsPage'
import PlaceholderPage from './pages/PlaceholderPage'

export default function App() {
  return (
    <Routes>
      {/* Public Login Route (outside AppLayout) */}
      <Route path="/login" element={<LoginPage />} />

      {/* Protected AppLayout Routes */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/events" replace />} />
          <Route path="/events" element={<EventsPage />} />

          {/* Organiser routes */}
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/volunteers" element={<VolunteersPage />} />
          <Route path="/volunteers/:id" element={<VolunteerProfilePage />} />
          <Route path="/assignment-plan" element={<AssignmentPlanPage />} />
          <Route path="/ai-assistant" element={<AiAssistantPage />} />
          <Route path="/feedback" element={<PlaceholderPage title="Feedback" />} />
          <Route path="/certificates" element={<PlaceholderPage title="Certificates" />} />
          <Route path="/settings" element={<SettingsPage />} />

          {/* Volunteer routes */}
          <Route path="/my-duties" element={<PlaceholderPage title="My Duties" />} />
          <Route path="/my-profile" element={<PlaceholderPage title="My Profile" />} />
          <Route path="/my-certificates" element={<PlaceholderPage title="My Certificates" />} />
        </Route>
      </Route>

      {/* Catch-all redirect */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
