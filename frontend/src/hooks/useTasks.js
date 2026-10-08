import { useState, useEffect } from 'react';
import { fetchTasks } from '../api';

export function useTasks(query, status, priority, page, pageSize) {
  const [tasks, setTasks] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    let isCancelled = false;

    fetchTasks({ query, status, priority, page, pageSize })
      .then((data) => {
        if (!isCancelled) {
          setTasks(data.items);
          setTotal(data.total);
          setLoading(false);
          setError(null);
        }
      })
      .catch((err) => {
        if (!isCancelled) {
          setError(err.message);
          setLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [query, status, priority, page, pageSize]);

  return { tasks, total, loading, error };
}
