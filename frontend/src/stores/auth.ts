import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import type { User, LoginRequest, RegisterRequest, AuthResponse, AccessLevel } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))

  const isAuthenticated = computed(() => !!token.value && !!user.value)

  const hasAccess = (level: AccessLevel): boolean => {
    if (!user.value) return false
    const hierarchy: Record<string, number> = {
      VIEWER: 0, USER: 1, CREATOR: 2, ADMIN: 3, SUPER: 4
    }
    return hierarchy[user.value.access] >= hierarchy[level]
  }

  // Set auth header for all requests
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  async function login(credentials: LoginRequest): Promise<void> {
    const response = await axios.post<AuthResponse>('/api/auth/login', credentials)
    token.value = response.data.token
    user.value = response.data.user
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  async function register(data: RegisterRequest): Promise<void> {
    const response = await axios.post<AuthResponse>('/api/auth/register', data)
    token.value = response.data.token
    user.value = response.data.user
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  async function fetchUser(): Promise<void> {
    if (!token.value) return
    try {
      const response = await axios.get<User>('/api/auth/me')
      user.value = response.data
    } catch {
      logout()
    }
  }

  function logout(): void {
    user.value = null
    token.value = null
    localStorage.removeItem('token')
    delete axios.defaults.headers.common['Authorization']
  }

  return {
    user,
    token,
    isAuthenticated,
    hasAccess,
    login,
    register,
    fetchUser,
    logout
  }
})
