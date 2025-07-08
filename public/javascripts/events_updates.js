// JavaScript for handling event updates via WebSocket
function showFlashMessage(message, type = 'success', reloadDelay = null) {
  // Create the flash message element
  const flashContainer = document.querySelector('.container.my-events-container');
  const flashDiv = document.createElement('div');
  flashDiv.className = type === 'success' ? 'success-message' : 'error-message';
  flashDiv.textContent = message;

  // Insert it at the top of the container
  if (flashContainer) {
    // Remove any existing flash messages
    const existingMessages = flashContainer.querySelectorAll('.success-message, .error-message');
    existingMessages.forEach(el => el.remove());

    // Add the new message as the first child
    flashContainer.insertBefore(flashDiv, flashContainer.firstChild);

  }
}

function createWebSocketIndicator() {
  const indicator = document.createElement('div');
  indicator.id = 'websocket-indicator';
  

  const text = document.createElement('span');
  text.id = 'websocket-text';
  
  indicator.appendChild(text);
  document.body.appendChild(indicator);
  
  return { indicator, text };
}

function createConnectionStatusBlock() {
  // Find the h1 element with "My Active Events"
  const h1Element = document.querySelector('h1');
  if (!h1Element) return null;
  
  // Create the status block
  const statusBlock = document.createElement('div');
  statusBlock.id = 'connection-status-block';
  statusBlock.style.cssText = `
    display: inline-block;
    margin-left: 15px;
    padding: 6px 12px;
    border-radius: 4px;
    font-size: 14px;
    font-weight: normal;
    vertical-align: middle;
  `;
  
  // Insert it right after the h1 text
  h1Element.appendChild(statusBlock);
  
  return statusBlock;
}

function updateConnectionStatusBlock(status, statusBlock) {
  if (!statusBlock) return;
  
  if (status === 'connected') {
    statusBlock.style.backgroundColor = '#d1fae5'; // Light green
    statusBlock.style.color = '#065f46'; // Dark green
    statusBlock.style.border = '1px solid #22c55e';
    statusBlock.textContent = '🟢 Conectado';
    statusBlock.style.display = 'inline-block';
  } else if (status === 'disconnected') {
    statusBlock.style.backgroundColor = '#fee2e2'; // Light red
    statusBlock.style.color = '#dc2626'; // Dark red
    statusBlock.style.border = '1px solid #ef4444';
    statusBlock.textContent = '🔴 No se están recibiendo mensajes - Refresca la página';
    statusBlock.style.display = 'inline-block';
  } else if (status === 'connecting') {
    statusBlock.style.backgroundColor = '#fef3c7'; // Light yellow
    statusBlock.style.color = '#d97706'; // Dark yellow
    statusBlock.style.border = '1px solid #f59e0b';
    statusBlock.textContent = '🟡 Conectando...';
    statusBlock.style.display = 'inline-block';
  }
}

document.addEventListener('DOMContentLoaded', function() {
  if (!("WebSocket" in window)) {
    console.log("WebSocket NOT supported by your Browser!");
    showFlashMessage('Tu navegador no soporta WebSocket', 'error');
    return;
  }
  
  // Create WebSocket indicator (top-right corner)
  const { indicator, text } = createWebSocketIndicator();
  
  // Create connection status block (next to title)
  const statusBlock = createConnectionStatusBlock();
  
  // Get WebSocket URL from script data attribute
  const getScriptParamUrl = function() {
    const scripts = document.getElementsByTagName('script');
    const lastScript = scripts[scripts.length-1];
    return lastScript.getAttribute('data-url');
  };
  
  let connection;
  let isManuallyDisconnected = false;
  
  function connectWebSocket() {
    const url = getScriptParamUrl();
    
    if (!url) {
      console.error('No WebSocket URL found');
      updateConnectionStatusBlock('disconnected', statusBlock);
      return;
    }
    
    connection = new WebSocket(url);

    updateConnectionStatusBlock('connecting', statusBlock);
    
    connection.onopen = function() {
      console.log('Events WebSocket connection established');
      updateConnectionStatusBlock('connected', statusBlock);
    };
    
    connection.onmessage = function(event) {
      console.log('WebSocket message received:', event.data);
      // Parse the message data if needed
      try {
        const data = JSON.parse(event.data);
        // Handle different message types here
        showFlashMessage('Actualización de evento recibida', 'success');
      } catch (e) {
        // If it's not JSON, treat as plain text
        showFlashMessage('Actualización de evento recibida', 'success');
      }
    };
    
    connection.onerror = function(error) {
      console.error('WebSocket error:', error);
      updateConnectionStatusBlock('disconnected', statusBlock);
    };
    
    connection.onclose = function(event) {
      console.log('WebSocket connection closed. Code:', event.code, 'Reason:', event.reason);
      updateConnectionStatusBlock('disconnected', statusBlock);
      
      // Don't attempt to reconnect, just show the disconnected state
      console.log('WebSocket disconnected. Manual refresh required.');
    };
  }
  
  // Handle page unload
  window.addEventListener('beforeunload', function() {
    isManuallyDisconnected = true;
    if (connection) {
      connection.close();
    }
  });
  
  // Initial connection
  connectWebSocket();
});