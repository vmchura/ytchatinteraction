// Add this JavaScript at the end of rivalTeamsEventForm.scala.html
document.addEventListener('DOMContentLoaded', function() {
  if ("WebSocket" in window) {
      console.log("WebSocket is supported by your Browser!");
    } else {
      console.log("WebSocket NOT supported by your Browser!");
      return;
    }

  const getScriptParamUrl = function() {
      const scripts = document.getElementsByTagName('script');
      const lastScript = scripts[scripts.length-1];
      return lastScript.getAttribute('data-url');
    };
  // Connect to WebSocket
  const url = getScriptParamUrl();
  const connection = new WebSocket(url);

  connection.onopen = function() {
    console.log('WebSocket connection established');
  };

  connection.onmessage = function(event) {
    // Parse the message data
    const data = JSON.parse(event.data);

    // Check if it's a vote detection message
    if (data.type === 'vote_detection') {
      // Find the table for this event
      const tableContainer = document.getElementById(data.eventId);

      if (tableContainer) {
        const tbody = tableContainer.querySelector('tbody');

        // Create a new row
        const row = document.createElement('tr');

        // Add cells with vote data
        row.innerHTML = `
          <td>${data.userName}</td>
          <td>${data.message}</td>
          <td>${data.optionText}</td>
          <td>${data.confidence}</td>
        `;

        // Add to table (prepend to show newest first)
        tbody.insertBefore(row, tbody.firstChild);

        // Limit to 10 rows to avoid too much content
      }
    }
  };

  connection.onerror = function(error) {
    console.error('WebSocket error:', error);
  };

  connection.onclose = function() {
    console.log('WebSocket connection closed');
  };
});