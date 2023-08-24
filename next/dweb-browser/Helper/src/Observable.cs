﻿namespace DwebBrowser.Helper;

public class Observable
{
    public record Change(string Key, dynamic NewValue, dynamic OldValue);

    public Listener<Change> Listener = new();

    private readonly List<Observer> observers = new();
    public List<Observer> Observers => observers.ToList();

    public Observer Observe(string key, dynamic initValue)
    {
        var observer = new Observer(this, key, initValue);

        observers.Add(observer);

        observer.OnListener += async (v, ov, _) =>
        {
            if (v.Equals(ov))
            {
                return;
            }

            await Listener.Emit(new Change(key, v, ov));
        };

        return observer;
    }

    public Observer ObserveNullable<T>(string key, dynamic? initValue = null)
    {
        Console.WriteLine($"key: {key} value: " + default(T));

        /// 字符串特殊处理，字符串默认值为null，修改为string.Empty，否则会出现空值无法赋值
        Observer observer;
        if (typeof(T).Name == "String")
        {
            observer = new Observer(this, key, string.Empty);
        }
        else
        {
            observer = new Observer(this, key, default(T));
        }

        observer.Set(initValue);

        observers.Add(observer);

        observer.OnListener += async (v, ov, _) =>
        {
            if (v.Equals(ov))
            {
                return;
            }

            await Listener.Emit(new Change(key, v, ov));
        };

        return observer;
    }

    public class Observer
    {
        private readonly HashSet<Signal<dynamic, dynamic>> signal = new();
        public event Signal<dynamic, dynamic> OnListener
        {
            add { if (value != null) lock (signal) { signal.Add(value); } }
            remove { lock (signal) { signal.Remove(value); } }
        }

        public dynamic Value { get; private set; }

        public dynamic Get()
        {
            return Value;
        }

        public void Set(dynamic value)
        {
            if (Value.Equals(value))
            {
                return;
            }

            var oldValue = Value;
            Value = value;

            _ = Ob.Listener.Emit(new Change(Key, value, oldValue));
        }

        private Observable Ob { get; init; }
        public string Key { get; init; }
        public Observer(Observable ob, string key, dynamic initValue)
        {
            Ob = ob;
            Key = key;
            Value = initValue;

            Ob.Observers.Add(this);
        }
    }
}
