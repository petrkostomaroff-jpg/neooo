NeoBum 1.0.0 - Paper 1.21.11, Java 25

СБОРКА .jar (нужны JDK 25 и Maven):
    mvn clean package
Готовый плагин: target/NeoBum.jar  ->  в папку plugins/

РЕСУРСПАК: NeoBum-ResourcePack.zip (pack_format 75) - раздать через server.properties
(resource-pack=ссылка, resource-pack-sha1=хеш) или поставить клиентам вручную.

КОМАНДЫ:
    /neobum give <игрок> <copper|iron|golden|diamond|netherite> [кол-во]
    /neobum reload
    /neobum list
Права: neobum.use (по умолчанию все), neobum.admin (op)
