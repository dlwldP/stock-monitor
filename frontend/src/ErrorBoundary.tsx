import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * Catches render/lifecycle errors in the component tree below it so a bug in one
 * card doesn't take down the whole page with a blank white screen. Does not catch
 * errors from event handlers or async code (React limitation) - those still need
 * their own try/catch, which the API-calling components already have.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled error in component tree:', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <main className="dashboard">
          <section className="card">
            <h2>문제가 발생했습니다</h2>
            <p className="error-text">{this.state.error.message}</p>
            <button type="button" onClick={() => window.location.reload()}>
              새로고침
            </button>
          </section>
        </main>
      )
    }
    return this.props.children
  }
}
