import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'react-hot-toast';
import AnimatedRoutes from './components/AnimatedRoutes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30000 },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Toaster
          position="top-right"
          toastOptions={{
            style: {
              background: '#1a1a2e',
              color: '#e0e0e0',
              border: '1px solid #2d2d4e',
              borderRadius: '12px',
            },
          }}
        />
          <AnimatedRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
