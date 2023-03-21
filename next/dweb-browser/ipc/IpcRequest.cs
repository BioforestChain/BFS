﻿using System;
using System.Numerics;
using System.Text.Json;
using System.Text.Json.Serialization;


namespace ipc
{
	public class IpcRequest
	{
		public IPC_MESSAGE_TYPE Type { get; set; } = IPC_MESSAGE_TYPE.REQUEST;
        public IpcMethod Method { get; set; } = IpcMethod.Get;

        public IpcRequest()
		{
		}
    }

    [JsonConverter(typeof(IpcReqMessageConvert))]
    public class IpcReqMessage : IpcMessage {
        public override IPC_MESSAGE_TYPE Type { get; set; } = IPC_MESSAGE_TYPE.REQUEST;

        public int ReqId { get; set; }
        public IpcMethod Method { get; set; }
        public string Url { get; set; }
        public Dictionary<string, string> Headers { get; set; }

        public IpcReqMessage(
            int req_id,
            IpcMethod method,
            string url,
            Dictionary<String, String> headers)
        {
            ReqId = req_id;
            Method = method;
            Url = url;
            Headers = headers;
        }

        internal IpcReqMessage()
        {
        }

        /// <summary>
        /// Serialize IpcReqMessage
        /// </summary>
        /// <returns>JSON string representation of the IpcReqMessage</returns>
        public string ToJson()
        {
            return JsonSerializer.Serialize(this);
        }

        /// <summary>
        /// Deserialize IpcReqMessage
        /// </summary>
        /// <param name="json">JSON string representation of IpcReqMessage</param>
        /// <returns>An instance of a IpcReqMessage object.</returns>
        public static IpcReqMessage? FromJson(string json)
        {
            return JsonSerializer.Deserialize<IpcReqMessage>(json);
        }
    }

    sealed class IpcReqMessageConvert : JsonConverter<IpcReqMessage>
    {
        public override bool CanConvert(Type typeToConvert) =>
            typeToConvert.GetMethod("ToJson") != null && typeToConvert.GetMethod("FromJson") != null;
        

        public override IpcReqMessage? Read(
            ref Utf8JsonReader reader,
            Type typeToConvert,
            JsonSerializerOptions options)
        {
            if (reader.TokenType != JsonTokenType.StartObject)
                throw new JsonException("Expected StartObject token");

            var ipcReqMessage = new IpcReqMessage();
            while (reader.Read())
            {
                if (reader.TokenType == JsonTokenType.EndObject)
                    return ipcReqMessage;

                if (reader.TokenType != JsonTokenType.PropertyName)
                    throw new JsonException("Expected PropertyName token");

                var propName = reader.GetString();

                reader.Read();

                switch (propName)
                {
                    case "req_id":
                        ipcReqMessage.ReqId = reader.GetInt32();
                        break;
                    case "type":
                        ipcReqMessage.Type = (IPC_MESSAGE_TYPE)reader.GetInt16();
                        break;
                    case "url":
                        ipcReqMessage.Url = reader.GetString() ?? "";
                        break;
                    case "method":
                        ipcReqMessage.Method = new IpcMethod(reader.GetString() ?? "GET");
                        break;
                    case "headers":
                        var headers = new Dictionary<string, string>();

                        while (reader.Read())
                        {
                            if (reader.TokenType == JsonTokenType.StartObject)
                            {
                                continue;
                            }

                            if (reader.TokenType == JsonTokenType.EndObject)
                            {
                                ipcReqMessage.Headers = headers;
                                break;
                            }

                            var memberName = reader.GetString();

                            reader.Read();

                            headers.Add(memberName, reader.GetString());
                        }


                        break;
                }
            }

            throw new JsonException("Expected EndObject token");
        }

        public override void Write(
            Utf8JsonWriter writer,
            IpcReqMessage value,
            JsonSerializerOptions options)
        {
            writer.WriteStartObject();

            writer.WriteNumber("req_id", value.ReqId);
            writer.WriteNumber("type", (int)value.Type);
            writer.WriteString("method", value.Method.method);
            writer.WriteString("url", value.Url);

            // dictionary
            writer.WritePropertyName("headers");
            writer.WriteStartObject();

            foreach ((string key, string keyValue) in value.Headers)
            {
                
                writer.WriteString(key, keyValue);
            }

            writer.WriteEndObject();

            writer.WriteEndObject();
        }
    }
}

