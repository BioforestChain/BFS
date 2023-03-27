namespace ipc.extensions;

static class ByteArrayExtensions
{
    public static string ToUtf8(this byte[] self) =>
        System.Text.UTF8Encoding.UTF8.GetString(self);

    public static string ToBase64(this byte[] self) =>
        Convert.ToBase64String(self);
}