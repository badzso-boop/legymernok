import React, { createContext, useContext, useRef, useState } from "react";

interface ChatContextValue {
  formFields: Record<string, string>;
  setFormFields: (fields: Record<string, string>) => void;
  registerFillCallback: (cb: ((fields: Record<string, string>) => void) | null) => void;
  triggerFill: (fields: Record<string, string>) => void;
}

const ChatContext = createContext<ChatContextValue | null>(null);

export const ChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [formFields, setFormFields] = useState<Record<string, string>>({});
  const fillCallbackRef = useRef<((fields: Record<string, string>) => void) | null>(null);

  const registerFillCallback = (cb: ((fields: Record<string, string>) => void) | null) => {
    fillCallbackRef.current = cb;
  };

  const triggerFill = (fields: Record<string, string>) => {
    if (fillCallbackRef.current) {
      fillCallbackRef.current(fields);
    }
  };

  return (
    <ChatContext.Provider value={{ formFields, setFormFields, registerFillCallback, triggerFill }}>
      {children}
    </ChatContext.Provider>
  );
};

export const useChatContext = (): ChatContextValue => {
  const ctx = useContext(ChatContext);
  if (!ctx) throw new Error("useChatContext must be used within ChatProvider");
  return ctx;
};
