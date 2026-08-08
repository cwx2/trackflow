import { ref, onUnmounted, watch, effectScope } from 'vue'
import { Client } from '@stomp/stompjs'
import type { StompSubscription } from '@stomp/stompjs'
import { useAuthStore } from '@/stores/auth'

/**
 * WebSocket 连接状态
 */
export type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

/**
 * Issue 实时变更事件（对应后端 IssueRealtimeEvent）
 */
export interface IssueRealtimeEvent {
  issueId: number
  projectId: number
  issueKey: string
  action: 'FIELD_UPDATED' | 'COMMENT_ADDED' | 'COMMENT_DELETED' | 'COMMENT_RESTORED' | 'TAG_CHANGED' | 'ATTACHMENT_CHANGED' | 'LINK_CHANGED' | 'CREATED' | 'DELETED'
  changes: Record<string, any>
  operatorId: number
  operatorName: string | null
  timestamp: string
}

// 全局单例 STOMP Client（所有组件共享一个连接）
let globalClient: Client | null = null
let globalStatus = ref<WsStatus>('disconnected')
let connectionRefCount = 0
let reconnectAttempts = 0
let isReconnecting = false // 标记是否处于"未成功重连"状态
const MAX_RECONNECT_ATTEMPTS = 5
const RECONNECT_DELAY_BASE = 5000 // 5s base
const RECONNECT_DELAY_MAX = 60000 // 60s cap

// Token 监听的 detached effect scope（不受任何组件卸载影响）
let tokenWatchScope: ReturnType<typeof effectScope> | null = null

/**
 * WebSocket 连接管理 composable。
 * 
 * 使用单例模式 — 多个组件共享同一个 WebSocket 连接。
 * 第一个组件调用 connect() 建立连接，最后一个组件 disconnect() 时才断开。
 */
export function useWebSocket() {
  const status = globalStatus

  /**
   * 确保 WebSocket 已连接。引用计数管理生命周期。
   */
  function connect(): Client | null {
    connectionRefCount++

    if (globalClient?.connected) {
      return globalClient
    }

    if (globalClient?.active) {
      // 正在连接中，等待
      return globalClient
    }

    const authStore = useAuthStore()
    const token = authStore.accessToken
    if (!token) {
      console.warn('[WebSocket] No access token, cannot connect')
      return null
    }

    const wsUrl = `ws://${window.location.hostname}:8090/ws`

    globalClient = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        token: token
      },
      // 心跳：客户端发送 10s，期望服务端 10s
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // 自动重连配置
      reconnectDelay: RECONNECT_DELAY_BASE,
      onConnect: () => {
        globalStatus.value = 'connected'
        reconnectAttempts = 0
        isReconnecting = false
        // Reset delay to base for next potential reconnection cycle
        if (globalClient) {
          globalClient.reconnectDelay = RECONNECT_DELAY_BASE
        }
        console.debug('[WebSocket] Connected')
      },
      onStompError: (frame) => {
        globalStatus.value = 'error'
        console.error('[WebSocket] STOMP error:', frame.headers['message'])
      },
      onWebSocketError: (event) => {
        globalStatus.value = 'error'
        console.warn('[WebSocket] Connection error')
      },
      onDisconnect: () => {
        globalStatus.value = 'disconnected'
        console.debug('[WebSocket] Disconnected')
      },
      onWebSocketClose: () => {
        if (connectionRefCount > 0) {
          // Only count as a consecutive failure if we were already trying to reconnect
          // (i.e., onConnect hasn't fired since the last close)
          if (isReconnecting) {
            reconnectAttempts++
          }
          isReconnecting = true

          if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            globalStatus.value = 'error'
            console.warn('[WebSocket] Max consecutive reconnect failures reached, giving up')
            // Stop STOMP client auto-reconnect
            if (globalClient) {
              globalClient.deactivate()
            }
            reconnectAttempts = 0
            isReconnecting = false
          } else {
            globalStatus.value = 'connecting'
            // Exponential backoff: 5s, 10s, 20s, 40s, 60s (capped)
            if (globalClient) {
              globalClient.reconnectDelay = Math.min(
                RECONNECT_DELAY_BASE * Math.pow(2, reconnectAttempts),
                RECONNECT_DELAY_MAX
              )
            }
            console.debug(
              `[WebSocket] Reconnecting (attempt ${reconnectAttempts + 1}/${MAX_RECONNECT_ATTEMPTS}, ` +
              `delay ${globalClient?.reconnectDelay ?? RECONNECT_DELAY_BASE}ms)`
            )
          }
        }
      }
    })

    globalStatus.value = 'connecting'
    globalClient.activate()

    // 注册 token 变化监听（仅一次），使用 detached effectScope 确保不受组件卸载影响
    if (!tokenWatchScope) {
      tokenWatchScope = effectScope(true) // detached = true
      tokenWatchScope.run(() => {
        watch(
          () => authStore.accessToken,
          (newToken) => {
            if (newToken && globalClient) {
              globalClient.connectHeaders = { token: newToken }
              // 如果连接已断开且仍有使用者，用新 token 重连
              if (!globalClient.connected && !globalClient.active && connectionRefCount > 0) {
                reconnectAttempts = 0 // 重置重连计数（新 token 应能成功）
                isReconnecting = false
                globalClient.reconnectDelay = RECONNECT_DELAY_BASE
                globalClient.activate()
              }
            }
          }
        )
      })
    }

    return globalClient
  }

  /**
   * 减少引用计数。当计数为 0 时断开连接。
   */
  function disconnect() {
    connectionRefCount = Math.max(0, connectionRefCount - 1)
    if (connectionRefCount === 0 && globalClient) {
      globalClient.deactivate()
      globalClient = null
      globalStatus.value = 'disconnected'
      reconnectAttempts = 0
      isReconnecting = false
      // 清理 token 监听 scope
      if (tokenWatchScope) {
        tokenWatchScope.stop()
        tokenWatchScope = null
      }
    }
  }

  /**
   * 获取当前 STOMP client（用于订阅）
   */
  function getClient(): Client | null {
    return globalClient
  }

  /**
   * Token 刷新后更新 WebSocket 连接 headers
   * @deprecated 已被 authStore.accessToken 的自动 watch 机制取代，保留仅为向后兼容
   */
  function updateToken(newToken: string) {
    if (globalClient) {
      globalClient.connectHeaders = { token: newToken }
      // 如果已断开，重新连接
      if (!globalClient.connected && connectionRefCount > 0) {
        globalClient.activate()
      }
    }
  }

  return {
    status,
    connect,
    disconnect,
    getClient,
    updateToken
  }
}

/**
 * 订阅项目级 Issue 变更事件。
 * 
 * 适用于 Issue 列表页 — 订阅当前项目的所有 Issue 变更。
 * 当 projectId 为 null 时（查看所有项目），不订阅任何 topic。
 * 
 * @param projectId 响应式项目 ID（getter 函数）
 * @param onEvent 收到事件时的回调
 */
export function useIssueProjectSubscription(
  projectId: () => string | number | null | undefined,
  onEvent: (event: IssueRealtimeEvent) => void
) {
  const { connect, disconnect, getClient, status } = useWebSocket()
  let subscription: StompSubscription | null = null
  let currentPid: string | number | null | undefined = null

  function subscribe() {
    unsubscribe()
    const pid = projectId()
    currentPid = pid
    if (!pid) return // 未选中项目时不订阅

    const client = getClient()
    if (!client?.connected) return

    const topic = `/topic/projects/${pid}/issues`
    subscription = client.subscribe(topic, (message) => {
      try {
        const event: IssueRealtimeEvent = JSON.parse(message.body)
        onEvent(event)
      } catch (e) {
        console.warn('[WebSocket] Failed to parse message:', e)
      }
    })
  }

  function unsubscribe() {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
  }

  // 当连接状态变为 connected 时自动订阅
  const stopStatusWatch = watch(status, (newStatus) => {
    if (newStatus === 'connected') {
      subscribe()
    }
  })

  // 监听 projectId 变化，重新订阅
  const stopPidWatch = watch(projectId, (newPid) => {
    if (newPid !== currentPid) {
      subscribe()
    }
  })

  // 初始连接
  connect()
  // 如果已经连接（从其他组件复用），立即订阅
  if (status.value === 'connected') {
    subscribe()
  }

  onUnmounted(() => {
    unsubscribe()
    disconnect()
    stopStatusWatch()
    stopPidWatch()
  })

  return {
    status,
    resubscribe: subscribe
  }
}

/**
 * 订阅单个 Issue 的变更事件。
 * 
 * 适用于 Issue 详情页 — 监听当前工单的所有变更。
 * 
 * @param issueId 响应式 Issue ID（getter 函数）
 * @param onEvent 收到事件时的回调
 */
export function useIssueDetailSubscription(
  issueId: () => string | number | null | undefined,
  onEvent: (event: IssueRealtimeEvent) => void
) {
  const { connect, disconnect, getClient, status } = useWebSocket()
  let subscription: StompSubscription | null = null
  let currentIid: string | number | null | undefined = null

  function subscribe() {
    unsubscribe()
    const iid = issueId()
    currentIid = iid
    if (!iid) return

    const client = getClient()
    if (!client?.connected) return

    const topic = `/topic/issues/${iid}`
    subscription = client.subscribe(topic, (message) => {
      try {
        const event: IssueRealtimeEvent = JSON.parse(message.body)
        onEvent(event)
      } catch (e) {
        console.warn('[WebSocket] Failed to parse message:', e)
      }
    })
  }

  function unsubscribe() {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
  }

  // 当连接状态变为 connected 时自动订阅
  const stopStatusWatch = watch(status, (newStatus) => {
    if (newStatus === 'connected') {
      subscribe()
    }
  })

  // 监听 issueId 变化，重新订阅
  const stopIidWatch = watch(issueId, (newIid) => {
    if (newIid !== currentIid) {
      subscribe()
    }
  })

  // 初始连接
  connect()
  if (status.value === 'connected') {
    subscribe()
  }

  onUnmounted(() => {
    unsubscribe()
    disconnect()
    stopStatusWatch()
    stopIidWatch()
  })

  return {
    status,
    resubscribe: subscribe
  }
}
