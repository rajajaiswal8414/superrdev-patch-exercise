export default function TaskTable({ tasks, loading, error }) {
  if (error) {
    return <div className="state-message error">Error: {error}</div>;
  }

  if (!loading && (!tasks || tasks.length === 0)) {
    return <div className="state-message">No tasks found.</div>;
  }

  return (
    <div className="table-container" style={{ position: 'relative', minHeight: '350px' }}>
      <table className="task-table" style={{ opacity: loading ? 0.5 : 1, transition: 'opacity 0.15s ease-in-out' }}>
        <thead>
          <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Assignee</th>
          </tr>
        </thead>
        <tbody>
          {tasks && tasks.length > 0 ? (
            tasks.map((task) => (
              <tr key={task.id}>
                <td>{task.id}</td>
                <td>
                  <div className="task-title">{task.title}</div>
                  <div className="task-desc">{task.description}</div>
                </td>
                <td>
                  <span className={`status-badge ${task.status.toLowerCase()}`}>{task.status}</span>
                </td>
                <td>{task.priority}</td>
                <td>{task.assignee || '\u2014'}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan="5" style={{ textAlign: 'center', padding: '2rem' }}>
                Loading tasks...
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
