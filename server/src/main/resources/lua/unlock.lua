local redis = redis
local KEYS = KEYS
local ARGV = ARGV

if redis.call('get',KEYS[1] == ARGV[1]) then
    return redis.call('del',KEYS[1])
else
    return 0
end
