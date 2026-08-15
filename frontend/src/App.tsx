import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { ChatProvider } from './context/ChatContext';

function App() {
  return (
    <ChatProvider>
      <RouterProvider router={router} />
    </ChatProvider>
  );
}

export default App;
